import {Alert, AppState, Platform} from 'react-native';
import {nativeModules} from '../../nativeModules';
import {flowLog} from '../flowLogger';
import {recoverScreenCaptureIfLost} from '../streamingPermissions';
import {
  ALL_PERMISSION_ORDER,
  PERMISSION_DEFS,
  RUNTIME_PERMISSION_ORDER,
  SCREEN_CAPTURE_ORDER,
  SPECIAL_PERMISSION_ORDER,
} from './catalog';
import type {GrantProgress, PermissionId} from './types';

const POLL_MS = 500;
const SETTINGS_WAIT_MS = 120_000;

let grantFlowGeneration = 0;

/** Cancel in-flight grant flow and disarm screen-capture auto-approve (e.g. app backgrounded). */
export function cancelActivePermissionGrant(): void {
  grantFlowGeneration += 1;
  void nativeModules.disarmScreenCaptureAutoApprove().catch(() => undefined);
}

function isFlowCancelled(generation: number): boolean {
  return generation !== grantFlowGeneration;
}

export type GrantOrchestratorCallbacks = {
  onProgress: (progress: GrantProgress) => void;
  /** Special permission — show explanation; resolve when user taps to continue. */
  onExplain: (id: PermissionId, title: string, body: string) => Promise<void>;
  /** Autostart — user confirms after visiting settings. */
  onManualConfirm: (id: PermissionId, title: string, body: string) => Promise<void>;
};

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/** MIUI may mark accessibility enabled while the service is still crashed — wait to reconnect. */
async function waitForAccessibilityConnected(timeoutMs = 15_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await nativeModules.isAccessibilityServiceConnected()) {
      return;
    }
    await delay(400);
  }
  throw new Error('ACCESSIBILITY_NOT_CONNECTED');
}

async function waitUntil(
  check: () => Promise<boolean>,
  generation: number,
  timeoutMs = SETTINGS_WAIT_MS,
): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (isFlowCancelled(generation)) {
      return false;
    }
    if (await check()) {
      return true;
    }
    await delay(POLL_MS);
  }
  return false;
}

async function waitForForeground(generation: number, timeoutMs = SETTINGS_WAIT_MS): Promise<boolean> {
  if (isFlowCancelled(generation)) {
    return false;
  }
  if (AppState.currentState === 'active') {
    await delay(350);
    return !isFlowCancelled(generation);
  }
  return new Promise<boolean>(resolve => {
    const deadline = Date.now() + timeoutMs;
    const sub = AppState.addEventListener('change', state => {
      if (state === 'active') {
        sub.remove();
        delay(350).then(() => resolve(!isFlowCancelled(generation)));
      }
    });
    const timer = setTimeout(() => {
      sub.remove();
      resolve(false);
    }, Math.max(0, deadline - Date.now()));
    // If generation cancelled, resolve false early via waitUntil checks downstream
    void timer;
  });
}

async function openPermission(id: PermissionId): Promise<void> {
  switch (id) {
    case 'location':
      await nativeModules.requestLocationPermission();
      break;
    case 'microphone':
      await nativeModules.requestMicrophonePermission();
      break;
    case 'camera':
      await nativeModules.requestCameraPermission();
      break;
    case 'postNotifications':
      await nativeModules.requestPostNotificationsPermission();
      break;
    case 'accessibility':
      await nativeModules.openAccessibilitySettings();
      break;
    case 'usageStats':
      await nativeModules.openUsageStatsSettings();
      break;
    case 'notificationAccess':
      await nativeModules.openNotificationListenerSettings();
      break;
    case 'overlay':
      await nativeModules.openOverlaySettings();
      break;
    case 'battery':
      await nativeModules.openBatteryOptimizationSettings();
      break;
    case 'autoStart':
      await nativeModules.openOemBackgroundSettings();
      break;
    case 'deviceAdmin':
      if (await nativeModules.isDeviceAdminActive()) {
        await nativeModules.openDeviceAdminSettings();
      } else {
        await nativeModules.requestDeviceAdmin();
      }
      break;
    case 'fullScreenIntent':
      await nativeModules.openFullScreenIntentSettings();
      break;
    case 'screenCapture':
      if (!(await nativeModules.isAccessibilityEnabled())) {
        throw new Error('ACCESSIBILITY_REQUIRED');
      }
      await waitForAccessibilityConnected(25_000);
      if (!(await nativeModules.isScreenCaptureAutoApproveReady())) {
        throw new Error('ACCESSIBILITY_NOT_CONNECTED');
      }
      await nativeModules.armScreenCaptureAutoApprove();
      try {
        await nativeModules.requestMediaProjectionConsent();
      } finally {
        await nativeModules.disarmScreenCaptureAutoApprove().catch(() => undefined);
      }
      break;
  }
}

async function isGranted(id: PermissionId): Promise<boolean> {
  switch (id) {
    case 'location':
      return nativeModules.isLocationGranted();
    case 'microphone':
      return nativeModules.isMicGranted();
    case 'camera':
      return nativeModules.isCameraGranted();
    case 'postNotifications':
      return Platform.OS === 'android' && Platform.Version >= 33
        ? nativeModules.isPostNotificationsGranted()
        : true;
    case 'accessibility':
      return (
        (await nativeModules.isAccessibilityEnabled()) &&
        (await nativeModules.isAccessibilityServiceConnected())
      );
    case 'usageStats':
      return nativeModules.isUsageStatsGranted();
    case 'notificationAccess':
      return nativeModules.isNotificationListenerEnabled();
    case 'overlay':
      return nativeModules.isOverlayGranted();
    case 'battery':
      return nativeModules.isBatteryOptimizationExempt();
    case 'autoStart':
      return (await nativeModules.requiresOemBackgroundSetup())
        ? nativeModules.isOemAutostartPromptDone()
        : true;
    case 'deviceAdmin':
      return nativeModules.isDeviceAdminActive();
    case 'fullScreenIntent':
      return Platform.OS === 'android' && Platform.Version >= 34
        ? nativeModules.isFullScreenIntentGranted()
        : true;
    case 'screenCapture':
      return (
        (await nativeModules.isMediaProjectionGranted()) &&
        (await nativeModules.isAccessibilityEnabled())
      );
  }
}

async function grantOnePermission(
  id: PermissionId,
  callbacks: Pick<GrantOrchestratorCallbacks, 'onExplain' | 'onManualConfirm'>,
  generation: number,
): Promise<boolean> {
  if (isFlowCancelled(generation) || (await isGranted(id))) {
    return await isGranted(id);
  }
  const def = PERMISSION_DEFS[id];

  if (def.kind === 'runtime') {
    if (isFlowCancelled(generation)) {
      return false;
    }
    await openPermission(id);
    return waitUntil(() => isGranted(id), generation, 60_000);
  }

  if (def.kind === 'special') {
    const body = [def.explanation, def.settingsHint].filter(Boolean).join('\n\n');
    await callbacks.onExplain(id, def.label, body);
    if (isFlowCancelled(generation)) {
      return false;
    }
    await openPermission(id);
    const returned = await waitForForeground(generation);
    if (!returned || isFlowCancelled(generation)) {
      return false;
    }
    if (def.manualConfirm) {
      await callbacks.onManualConfirm(
        id,
        def.label,
        'When you have enabled autostart / background activity, tap OK to continue.',
      );
      if (isFlowCancelled(generation)) {
        return false;
      }
      await nativeModules.markOemAutostartPromptDone();
    }
    return waitUntil(() => isGranted(id), generation);
  }

  // screen capture — wait for accessibility service, then auto-consent
  if (!(await nativeModules.isAccessibilityEnabled())) {
    return false;
  }
  let autoApproveReady = await nativeModules.isScreenCaptureAutoApproveReady();
  if (!autoApproveReady) {
    await waitForAccessibilityConnected(25_000);
    autoApproveReady = await nativeModules.isScreenCaptureAutoApproveReady();
  }
  if (!autoApproveReady) {
    await callbacks.onExplain(id, def.label, def.explanation);
    if (isFlowCancelled(generation)) {
      return false;
    }
  }
  try {
    await openPermission(id);
  } catch (e) {
    const msg = String(e);
    if (msg.includes('ACCESSIBILITY_REQUIRED') || msg.includes('ACCESSIBILITY_NOT_CONNECTED')) {
      return false;
    }
    throw e;
  }
  if (isFlowCancelled(generation)) {
    return false;
  }
  let ok = await waitUntil(() => isGranted(id), generation, 45_000);
  if (!ok && !isFlowCancelled(generation)) {
    ok = await recoverScreenCaptureIfLost();
  }
  return ok;
}

/** Grant a single permission from the dashboard toggle. */
export async function grantSinglePermission(
  id: PermissionId,
  callbacks: Pick<GrantOrchestratorCallbacks, 'onExplain' | 'onManualConfirm'>,
): Promise<boolean> {
  grantFlowGeneration += 1;
  const generation = grantFlowGeneration;
  return grantOnePermission(id, callbacks, generation);
}

/** Open settings to revoke or manage a granted permission (toggle off / row tap when granted). */
export async function revokeOrManagePermission(id: PermissionId): Promise<void> {
  switch (id) {
    case 'accessibility':
      await nativeModules.launchTamperGateForAction('accessibility');
      break;
    case 'screenCapture':
      await nativeModules.clearMediaProjectionConsent();
      break;
    case 'deviceAdmin':
      await nativeModules.launchTamperGateForAction('device_admin');
      break;
    case 'location':
      await nativeModules.requestLocationPermission();
      break;
    case 'microphone':
    case 'camera':
      await nativeModules.requestCameraAndMicPermissions();
      break;
    case 'postNotifications':
      await nativeModules.requestPostNotificationsPermission();
      break;
    case 'usageStats':
      await nativeModules.openUsageStatsSettings();
      break;
    case 'notificationAccess':
      await nativeModules.openNotificationListenerSettings();
      break;
    case 'overlay':
      await nativeModules.openOverlaySettings();
      break;
    case 'battery':
      await nativeModules.openBatteryOptimizationSettings();
      break;
    case 'autoStart':
      await nativeModules.openOemBackgroundSettings();
      break;
    case 'fullScreenIntent':
      await nativeModules.openFullScreenIntentSettings();
      break;
  }
}
function missingIds(order: PermissionId[]): Promise<PermissionId[]> {
  return Promise.all(
    order.map(async id => ((await isGranted(id)) ? null : id)),
  ).then(results => results.filter((id): id is PermissionId => id !== null));
}

async function grantRuntime(
  ids: PermissionId[],
  callbacks: GrantOrchestratorCallbacks,
  baseIndex: number,
  total: number,
  generation: number,
): Promise<void> {
  if (ids.length === 0 || isFlowCancelled(generation)) {
    return;
  }
  callbacks.onProgress({
    phase: 'runtime',
    stepId: 'location',
    stepIndex: baseIndex + 1,
    stepTotal: total,
    label: 'Location, microphone, camera & notifications',
  });
  flowLog('PERMISSION_FLOW', 'grant runtime batch', {ids});
  await nativeModules.requestAllRuntimePermissions();
  if (isFlowCancelled(generation)) {
    return;
  }
  for (const id of ids) {
    if (!(await isGranted(id))) {
      flowLog('PERMISSION_FLOW', 'runtime not granted after batch', {id});
    }
  }
}

async function grantSpecial(
  ids: PermissionId[],
  callbacks: GrantOrchestratorCallbacks,
  baseIndex: number,
  total: number,
  generation: number,
): Promise<void> {
  for (let i = 0; i < ids.length; i++) {
    if (isFlowCancelled(generation)) {
      return;
    }
    const id = ids[i];
    const def = PERMISSION_DEFS[id];
    callbacks.onProgress({
      phase: 'special',
      stepId: id,
      stepIndex: baseIndex + i + 1,
      stepTotal: total,
      label: def.label,
    });
    flowLog('PERMISSION_FLOW', 'grant special', {id});
    const ok = await grantOnePermission(id, callbacks, generation);
    if (!ok) {
      flowLog('PERMISSION_FLOW', 'special not granted', {id});
      Alert.alert(
        'Permission not detected',
        `${def.label} is still off. We'll continue — you can enable it from the dashboard.`,
      );
    }
  }
}

async function grantScreenCapture(
  callbacks: GrantOrchestratorCallbacks,
  stepIndex: number,
  total: number,
  generation: number,
): Promise<void> {
  const id: PermissionId = 'screenCapture';
  const def = PERMISSION_DEFS[id];
  callbacks.onProgress({
    phase: 'screen',
    stepId: id,
    stepIndex,
    stepTotal: total,
    label: def.label,
  });
  flowLog('PERMISSION_FLOW', 'grant screen capture');
  if (isFlowCancelled(generation) || (await isGranted(id))) {
    return;
  }
  const ok = await grantOnePermission(id, callbacks, generation);
  if (!ok) {
    Alert.alert(
      'Screen capture not saved',
      'Tap Screen Capture on the dashboard to try again.',
    );
  }
}

/** Sequential FlashGet-style grant: runtime → special → screen capture. Returns false if cancelled (e.g. app backgrounded). */
export async function grantAllPermissions(
  callbacks: GrantOrchestratorCallbacks,
): Promise<boolean> {
  grantFlowGeneration += 1;
  const generation = grantFlowGeneration;
  const runtimeMissing = await missingIds(RUNTIME_PERMISSION_ORDER);
  const specialMissing = await missingIds(SPECIAL_PERMISSION_ORDER);
  const screenMissing = await missingIds(SCREEN_CAPTURE_ORDER);
  const total = runtimeMissing.length + specialMissing.length + screenMissing.length;

  if (total === 0) {
    callbacks.onProgress({
      phase: 'done',
      stepId: null,
      stepIndex: 0,
      stepTotal: 0,
      label: 'All permissions granted',
    });
    return true;
  }

  let index = 0;
  if (runtimeMissing.length > 0) {
    await grantRuntime(runtimeMissing, callbacks, index, total, generation);
    index += runtimeMissing.length;
  }
  if (specialMissing.length > 0 && !isFlowCancelled(generation)) {
    await grantSpecial(specialMissing, callbacks, index, total, generation);
    index += specialMissing.length;
  }
  if (screenMissing.length > 0 && !isFlowCancelled(generation)) {
    await grantScreenCapture(callbacks, index + 1, total, generation);
  }

  if (isFlowCancelled(generation)) {
    return false;
  }

  callbacks.onProgress({
    phase: 'done',
    stepId: null,
    stepIndex: total,
    stepTotal: total,
    label: 'Setup complete',
  });
  return true;
}

export function permissionOrderLabel(): string {
  return ALL_PERMISSION_ORDER.map(id => PERMISSION_DEFS[id].label).join(' → ');
}
