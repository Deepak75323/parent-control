import {Platform} from 'react-native';
import {nativeModules} from '../nativeModules';
import {flowLog} from './flowLogger';

/** Camera-only + tamper setup keys (no location / screen share / usage / overlay). */
export type SetupPermissionKey =
  | 'accessibility'
  | 'notification'
  | 'camera'
  | 'battery'
  | 'oemBackground'
  | 'deviceAdmin';

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
      key: 'camera',
      title: 'Camera',
      check: () => nativeModules.isCameraGranted(),
      open: async () => ({
        mode: 'in_app',
        granted: await nativeModules.requestCameraPermission(),
      }),
      inApp: true,
    },
  ];

  if (Platform.OS === 'android' && Platform.Version >= 33) {
    steps.push({
      key: 'notification',
      title: 'Notifications',
      check: () => nativeModules.isPostNotificationsGranted(),
      open: async () => ({
        mode: 'in_app',
        granted: await nativeModules.requestPostNotificationsPermission(),
      }),
      inApp: true,
    });
  }

  steps.push(
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
      key: 'battery',
      title: 'Battery unrestricted',
      check: () => nativeModules.isBatteryOptimizationExempt(),
      open: async () => {
        await nativeModules.openBatteryOptimizationSettings();
        return {mode: 'settings'};
      },
    },
  );

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

  steps.push({
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
  });

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

/** Camera-only + tamper — every required setup permission must be granted. */
export async function isSetupPermissionsComplete(): Promise<boolean> {
  const requiresOem = await nativeModules.requiresOemBackgroundSetup();
  const needsPostNotifications = Platform.OS === 'android' && Platform.Version >= 33;
  const [camera, postNotifications, accessibility, battery, oemBackground, deviceAdmin] =
    await Promise.all([
      nativeModules.isCameraGranted(),
      needsPostNotifications
        ? nativeModules.isPostNotificationsGranted()
        : Promise.resolve(true),
      nativeModules.isAccessibilityEnabled(),
      nativeModules.isBatteryOptimizationExempt(),
      requiresOem ? nativeModules.isOemAutostartPromptDone() : Promise.resolve(true),
      nativeModules.isDeviceAdminActive(),
    ]);

  const accessibilityConnected = accessibility
    ? await nativeModules.isAccessibilityServiceConnected()
    : false;

  return (
    camera &&
    postNotifications &&
    accessibility &&
    accessibilityConnected &&
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
