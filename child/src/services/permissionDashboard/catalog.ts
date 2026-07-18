import {Platform} from 'react-native';
import type {PermissionDef, PermissionGroupDef, PermissionId} from './types';

export const RUNTIME_PERMISSION_ORDER: PermissionId[] = [
  'location',
  'microphone',
  'camera',
  ...(Platform.OS === 'android' && Platform.Version >= 33
    ? (['postNotifications'] as PermissionId[])
    : []),
];

export const SPECIAL_PERMISSION_ORDER: PermissionId[] = [
  'accessibility',
  'usageStats',
  'notificationAccess',
  'overlay',
  'battery',
  'autoStart',
  'deviceAdmin',
  ...(Platform.OS === 'android' && Platform.Version >= 34
    ? (['fullScreenIntent'] as PermissionId[])
    : []),
];

export const SCREEN_CAPTURE_ORDER: PermissionId[] = ['screenCapture'];

export const ALL_PERMISSION_ORDER: PermissionId[] = [
  ...RUNTIME_PERMISSION_ORDER,
  ...SPECIAL_PERMISSION_ORDER,
  ...SCREEN_CAPTURE_ORDER,
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
    explanation: 'Allow the microphone so your parent can listen during live listening.',
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
    explanation: 'Allow notifications so Clock Time can show important setup reminders.',
  },
  accessibility: {
    id: 'accessibility',
    kind: 'special',
    label: 'Required device feature',
    explanation:
      'Turn on Clock Time in the next settings list. This helps block apps, manage screen time, and finish screen sharing smoothly. Turning it off needs a parent password or code.',
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
    explanation: 'Allow Clock Time to keep running normally when the phone tries to save battery.',
    settingsHint: 'Choose Don’t optimize / Unrestricted / Allow for Clock Time.',
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
      'Activate device protection so Clock Time can’t be removed easily. A parent password or code is needed to turn this off.',
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
      description: 'Location, microphone, camera, and notifications — tap Allow when asked.',
      permissionIds: [...RUNTIME_PERMISSION_ORDER],
    },
    {
      id: 'usageLimits',
      icon: '🛡️',
      title: 'Usage limits',
      description: 'Limit apps and screen time on this phone.',
      permissionIds: ['accessibility', 'overlay'],
    },
    {
      id: 'syncNotifications',
      icon: '📢',
      title: 'App alerts',
      description: 'Let your parent see notifications from this phone.',
      permissionIds:
        Platform.OS === 'android' && Platform.Version >= 34
          ? (['notificationAccess', 'fullScreenIntent'] as PermissionId[])
          : (['notificationAccess'] as PermissionId[]),
    },
    {
      id: 'usageReport',
      icon: '📊',
      title: 'Usage reports',
      description: 'Share screen time and app usage with your parent.',
      permissionIds: ['usageStats'],
    },
    {
      id: 'background',
      icon: '🔋',
      title: 'Keep Clock Time running',
      description: 'Help Clock Time keep working after restart and when it isn’t open.',
      permissionIds: backgroundIds,
    },
    {
      id: 'screenShare',
      icon: '📺',
      title: 'Screen sharing',
      description: 'Allow your parent to view this phone’s screen when needed.',
      permissionIds: ['screenCapture'],
    },
  ];
}
