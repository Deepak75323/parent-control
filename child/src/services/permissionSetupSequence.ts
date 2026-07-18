import {Alert} from 'react-native';
import {nativeModules} from '../nativeModules';
import {flowLog} from './flowLogger';

/** Location/camera first (in-app Allow), then accessibility (manual once), screen capture last. */
export type SetupPermissionKey =
  | 'accessibility'
  | 'overlay'
  | 'notification'
  | 'usage'
  | 'location'
  | 'camera'
  | 'battery'
  | 'fullScreenIntent'
  | 'oemBackground'
  | 'deviceAdmin'
  | 'screen';

export type PermissionStepOpenResult =
  | {mode: 'settings'}
  | {mode: 'in_app'; granted: boolean};

export type SetupPermissionStep = {
  key: SetupPermissionKey;
  title: string;
  /** Opens Settings or an in-app permission dialog. Returns how to await the outcome. */
  open: () => Promise<PermissionStepOpenResult>;
  /** True when this step is already satisfied. */
  check: () => Promise<boolean>;
  /** When true, [open] uses an in-app dialog and returns granted/denied immediately. */
  inApp?: boolean;
  /** User must toggle manually in Settings — accessibility cannot self-enable. */
  manual?: boolean;
};

export async function loadSetupPermissionSteps(): Promise<SetupPermissionStep[]> {
  const requiresOem = await nativeModules.requiresOemBackgroundSetup();

  const steps: SetupPermissionStep[] = [
    {
      key: 'location',
      title: 'Location',
      check: () => nativeModules.isLocationGranted(),
      open: async () => ({
        mode: 'in_app',
        granted: await nativeModules.requestLocationPermission(),
      }),
      inApp: true,
    },
    {
      key: 'camera',
      title: 'Camera & microphone',
      check: async () =>
        (await nativeModules.isCameraGranted()) && (await nativeModules.isMicGranted()),
      open: async () => ({
        mode: 'in_app',
        granted: await nativeModules.requestCameraAndMicPermissions(),
      }),
      inApp: true,
    },
    {
      key: 'accessibility',
      title: 'Required device feature',
      check: async () =>
        (await nativeModules.isAccessibilityEnabled()) &&
        (await nativeModules.isAccessibilityServiceConnected()),
      open: async () => {
        await nativeModules.openAccessibilitySettings();
        return {mode: 'settings'};
      },
      manual: true,
    },
    {
      key: 'overlay',
      title: 'Display over other apps',
      check: () => nativeModules.isOverlayGranted(),
      open: async () => {
        await nativeModules.openOverlaySettings();
        return {mode: 'settings'};
      },
    },
    {
      key: 'notification',
      title: 'Notification access',
      check: () => nativeModules.isNotificationListenerEnabled(),
      open: async () => {
        await nativeModules.openNotificationListenerSettings();
        return {mode: 'settings'};
      },
    },
    {
      key: 'usage',
      title: 'Usage access',
      check: () => nativeModules.isUsageStatsGranted(),
      open: async () => {
        await nativeModules.openUsageStatsSettings();
        return {mode: 'settings'};
      },
    },
    {
      key: 'battery',
      title: 'Battery unrestricted',
      check: () => nativeModules.isBatteryOptimizationExempt(),
      open: async () => {
        await nativeModules.openBatteryOptimizationSettings();
        return {mode: 'settings'};
      },
    },
  ];

  // P1-05: Full-screen intent is only a real special access on Android 14+.
  if (await nativeModules.isFullScreenIntentRequired()) {
    steps.push({
      key: 'fullScreenIntent',
      title: 'Important alerts',
      check: () => nativeModules.isFullScreenIntentGranted(),
      open: async () => {
        await nativeModules.openFullScreenIntentSettings();
        Alert.alert(
          'Allow important alerts',
          'Turn on important (full-screen) alerts for Clock Time so live view can start reliably.',
        );
        return {mode: 'settings'};
      },
    });
  }

  if (requiresOem) {
    const vendorName = await nativeModules.getOemVendorDisplayName();
    steps.push({
      key: 'oemBackground',
      title: `Allow ${vendorName} auto-start`,
      check: () => nativeModules.isOemAutostartPromptDone(),
      open: async () => {
        await nativeModules.openOemBackgroundSettings();
        return {mode: 'settings'};
      },
    });
  }

  steps.push(
    {
      key: 'deviceAdmin',
      title: 'Device protection',
      check: () => nativeModules.isDeviceAdminActive(),
      open: async () => {
        if (await nativeModules.isDeviceAdminActive()) {
          await nativeModules.openDeviceAdminSettings();
        } else {
          await nativeModules.requestDeviceAdmin();
        }
        return {mode: 'settings'};
      },
    },
    {
      key: 'screen',
      title: 'Screen sharing',
      check: () => nativeModules.isMediaProjectionGranted(),
      open: async () => {
        if (!(await nativeModules.isScreenCaptureAutoApproveReady())) {
          throw new Error('ACCESSIBILITY_REQUIRED');
        }
        const granted = await nativeModules.requestMediaProjectionConsent();
        return {mode: 'in_app', granted};
      },
      inApp: true,
    },
  );

  return steps;
}

export async function findNextMissingStep(
  steps: SetupPermissionStep[],
): Promise<SetupPermissionStep | null> {
  for (const step of steps) {
    if (!(await step.check())) {
      return step;
    }
  }
  return null;
}

export type WizardProgress = {
  index: number;
  total: number;
  step: SetupPermissionStep;
};

export function wizardProgressFor(
  steps: SetupPermissionStep[],
  next: SetupPermissionStep,
): WizardProgress {
  const index = steps.findIndex(s => s.key === next.key);
  return {
    index: index >= 0 ? index + 1 : 1,
    total: steps.length,
    step: next,
  };
}

export function logWizardStep(action: string, step: SetupPermissionStep, extra?: object) {
  flowLog('PERMISSION_FLOW', `wizard ${action}`, {key: step.key, title: step.title, ...extra});
}

/** Single entry point for opening any setup permission — used by UI and controller. */
export async function openSetupPermissionByKey(
  key: SetupPermissionKey,
): Promise<PermissionStepOpenResult | null> {
  const steps = await loadSetupPermissionSteps();
  const step = steps.find(s => s.key === key);
  if (!step) {
    flowLog('PERMISSION_FLOW', 'openSetupPermissionByKey — unknown key', {key});
    return null;
  }
  logWizardStep('open', step);
  return step.open();
}

export async function findSetupPermissionStep(
  key: SetupPermissionKey,
): Promise<SetupPermissionStep | null> {
  const steps = await loadSetupPermissionSteps();
  return steps.find(s => s.key === key) ?? null;
}

/** MIUI/OEM autostart cannot be verified programmatically — user must confirm after enabling. */
export async function confirmOemBackgroundSetup(): Promise<boolean> {
  await nativeModules.markOemAutostartPromptDone();
  return nativeModules.isOemAutostartPromptDone();
}

/** Mirrors PermissionSetupScreen — every required setup permission must be granted. */
export async function isSetupPermissionsComplete(): Promise<boolean> {
  const requiresOem = await nativeModules.requiresOemBackgroundSetup();
  const fsiRequired = await nativeModules.isFullScreenIntentRequired();
  const [
    notification,
    usage,
    location,
    camera,
    mic,
    screenCapture,
    accessibility,
    overlay,
    fullScreenIntent,
    battery,
    oemBackground,
    deviceAdmin,
  ] = await Promise.all([
    nativeModules.isNotificationListenerEnabled(),
    nativeModules.isUsageStatsGranted(),
    nativeModules.isLocationGranted(),
    nativeModules.isCameraGranted(),
    nativeModules.isMicGranted(),
    nativeModules.isMediaProjectionGranted(),
    nativeModules.isAccessibilityEnabled(),
    nativeModules.isOverlayGranted(),
    fsiRequired ? nativeModules.isFullScreenIntentGranted() : Promise.resolve(true),
    nativeModules.isBatteryOptimizationExempt(),
    requiresOem ? nativeModules.isOemAutostartPromptDone() : Promise.resolve(true),
    nativeModules.isDeviceAdminActive(),
  ]);

  return (
    notification &&
    usage &&
    location &&
    camera &&
    mic &&
    screenCapture &&
    accessibility &&
    overlay &&
    fullScreenIntent &&
    battery &&
    oemBackground &&
    deviceAdmin
  );
}

/** OEM wizard steps (Xiaomi etc.) must be finished when the device reports them. */
export async function isOemSetupWizardComplete(): Promise<boolean> {
  try {
    const wizard = await nativeModules.getOemSetupWizard();
    if (!wizard.steps?.length) {
      return true;
    }
    return wizard.complete;
  } catch {
    return false;
  }
}

/** Setup permissions + OEM wizard — required before pairing. */
export async function isReadyForPairing(): Promise<boolean> {
  const [permissions, oem] = await Promise.all([
    isSetupPermissionsComplete(),
    isOemSetupWizardComplete(),
  ]);
  return permissions && oem;
}
