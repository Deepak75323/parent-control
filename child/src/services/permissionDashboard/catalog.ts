import {Platform} from 'react-native';
import type {PermissionDef, PermissionGroupDef, PermissionId} from './types';

/**
 * Camera-with-sound + tamper keep list.
 * Still defined in PERMISSION_DEFS for status sync, but not shown/required in setup.
 */
export const RUNTIME_PERMISSION_ORDER: PermissionId[] = [
  'camera',
  'microphone',
  ...(Platform.OS === 'android' && Platform.Version >= 33
    ? (['postNotifications'] as PermissionId[])
    : []),
];

export const SPECIAL_PERMISSION_ORDER: PermissionId[] = [
  'accessibility',
  'battery',
  'autoStart',
  'deviceAdmin',
];

/** Screen share removed from camera-only setup. */
export const SCREEN_CAPTURE_ORDER: PermissionId[] = [];

export const ALL_PERMISSION_ORDER: PermissionId[] = [
  ...RUNTIME_PERMISSION_ORDER,
  ...SPECIAL_PERMISSION_ORDER,
  ...SCREEN_CAPTURE_ORDER,
];

/** Permissions intentionally not required for camera-with-sound + tamper. */
export const OPTIONAL_PERMISSION_IDS: PermissionId[] = [
  'location',
  'usageStats',
  'notificationAccess',
  'overlay',
  'fullScreenIntent',
  'screenCapture',
];

export const PERMISSION_DEFS: Record<PermissionId, PermissionDef> = {
  location: {
    id: 'location',
    kind: 'runtime',
    label: 'Location',
    explanation:
      'Allow location so your parent can see where this phone is. On the next screen, choose “While using the app” or “Allow all the time.”',
    settingsHint:
      'For location when the app is closed: Settings → Apps → Clock Time → Location → Allow all the time.',
  },
  microphone: {
    id: 'microphone',
    kind: 'runtime',
    label: 'Microphone',
    explanation: 'Allow the microphone so your parent can hear room sound during live camera.',
  },
  camera: {
    id: 'camera',
    kind: 'runtime',
    label: 'Camera',
    explanation: 'Allow the camera so your parent can see the camera view during live camera.',
  },
  postNotifications: {
    id: 'postNotifications',
    kind: 'runtime',
    label: 'Notifications',
    explanation:
      'Allow notifications so Clock Time can stay running and show when your parent starts live camera.',
  },
  accessibility: {
    id: 'accessibility',
    kind: 'special',
    label: 'Required device feature',
    explanation:
      'Turn on Clock Time in the next settings list. This protects Clock Time from being turned off or removed without a parent password.',
    settingsHint: 'Find “Clock Time” in the list and turn it on.',
  },
  usageStats: {
    id: 'usageStats',
    kind: 'special',
    label: 'App usage access',
    explanation: 'Allow usage access so your parent can see screen time and which apps were used.',
    settingsHint: 'Find “Clock Time” and allow usage access.',
  },
  notificationAccess: {
    id: 'notificationAccess',
    kind: 'special',
    label: 'Notification access',
    explanation: 'Allow notification access so your parent can see alerts from apps on this phone.',
    settingsHint: 'Find “Clock Time” and turn notification access on.',
  },
  overlay: {
    id: 'overlay',
    kind: 'special',
    label: 'Display over other apps',
    explanation: 'Allow Clock Time to show block screens and reminders on top of other apps.',
    settingsHint: 'Turn on “Allow display over other apps” for Clock Time.',
  },
  battery: {
    id: 'battery',
    kind: 'special',
    label: 'Battery unrestricted',
    explanation:
      'Allow Clock Time to keep running when the phone saves battery. On Xiaomi HyperOS use Battery optimisation → All apps → Clock Time → Not optimised (there may be no “No restrictions” row).',
    settingsHint: 'Battery optimisation → All apps → Clock Time → Not optimised.',
  },
  autoStart: {
    id: 'autoStart',
    kind: 'special',
    label: 'Allow auto-start',
    explanation:
      'On this phone, allow Clock Time to start after restart and keep working when it isn’t open.',
    settingsHint: 'Turn on autostart or unrestricted background for Clock Time.',
    manualConfirm: true,
  },
  deviceAdmin: {
    id: 'deviceAdmin',
    kind: 'special',
    label: 'Device protection',
    explanation:
      'Activate device protection so Clock Time is harder to remove. Use the toggle below if turning it off should require a parent password.',
    settingsHint: 'Activate Clock Time as a device administrator.',
  },
  fullScreenIntent: {
    id: 'fullScreenIntent',
    kind: 'special',
    label: 'Important alerts',
    explanation:
      'Allow Clock Time to show important alerts when the phone is locked so live view can start reliably.',
    settingsHint:
      'Settings → Apps → Special app access → Full screen intents → turn on for Clock Time.',
  },
  screenCapture: {
    id: 'screenCapture',
    kind: 'screen',
    label: 'Screen sharing',
    explanation:
      'Next, allow screen sharing so your parent can view this phone’s screen. It may finish automatically after you confirm.',
  },
};

export function buildPermissionGroups(requiresAutoStart: boolean): PermissionGroupDef[] {
  const backgroundIds: PermissionId[] = requiresAutoStart
    ? ['battery', 'autoStart', 'deviceAdmin']
    : ['battery', 'deviceAdmin'];

  return [
    {
      id: 'basicRuntime',
      icon: '✅',
      title: 'Basic permissions',
      description: 'Camera and notifications — tap Allow when asked.',
      permissionIds: [...RUNTIME_PERMISSION_ORDER],
    },
    {
      id: 'tamperProtection',
      icon: '🛡️',
      title: 'Uninstall protection',
      description: 'Keep Clock Time from being turned off or removed without a parent password.',
      permissionIds: ['accessibility'],
    },
    {
      id: 'background',
      icon: '🔋',
      title: 'Keep Clock Time running',
      description: 'Help Clock Time keep working after restart and when it isn’t open.',
      permissionIds: backgroundIds,
    },
  ];
}
