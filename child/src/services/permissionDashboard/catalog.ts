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
  // P1-05: Android 14+ special app access — required for BAL fullScreenIntent fallback.
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
    label: 'Location Permission',
    explanation:
      'Allow location access so your parent can see this device on a map and set geofences. Choose "While using the app" or "Allow all the time" on the next screen.',
    settingsHint:
      'For background tracking, open Settings → Permissions → Location and choose "Allow all the time" after granting.',
  },
  microphone: {
    id: 'microphone',
    kind: 'runtime',
    label: 'Microphone Permission',
    explanation: 'Allow microphone access for one-way parent audio monitoring during live view.',
  },
  camera: {
    id: 'camera',
    kind: 'runtime',
    label: 'Camera Permission',
    explanation: 'Allow camera access so your parent can see the device camera during live view.',
  },
  postNotifications: {
    id: 'postNotifications',
    kind: 'runtime',
    label: 'Notifications',
    explanation: 'Allow notifications so monitoring status and parent requests can reach this device.',
  },
  accessibility: {
    id: 'accessibility',
    kind: 'special',
    label: 'Accessibility Permission',
    explanation:
      'Accessibility lets this app read screen content, block apps, and auto-approve screen sharing when needed. Turning it off requires the parent PIN or verification code.',
    settingsHint: 'Find "Device Monitor" in the list and turn the service ON.',
  },
  usageStats: {
    id: 'usageStats',
    kind: 'special',
    label: 'User Usage Permission',
    explanation: 'Usage access shows screen time and app usage to your parent.',
    settingsHint: 'Find "Device Monitor" and allow usage access.',
  },
  notificationAccess: {
    id: 'notificationAccess',
    kind: 'special',
    label: 'Notifications Access',
    explanation: 'Notification access syncs incoming alerts for the parent dashboard.',
    settingsHint: 'Find "Device Monitor" and enable notification access.',
  },
  overlay: {
    id: 'overlay',
    kind: 'special',
    label: 'Allow Display Over Other Apps',
    explanation: 'Overlay permission shows block screens and alerts on top of other apps.',
    settingsHint: 'Enable "Allow display over other apps" for Device Monitor.',
  },
  battery: {
    id: 'battery',
    kind: 'special',
    label: 'Ignore Battery Optimization',
    explanation: 'Disable battery optimization so monitoring keeps running in the background.',
    settingsHint: 'Choose "Don\'t optimize" or Allow for Device Monitor.',
  },
  autoStart: {
    id: 'autoStart',
    kind: 'special',
    label: 'Allow auto-start',
    explanation:
      'On this phone, allow autostart / background activity so monitoring survives reboots and when the app is hidden.',
    settingsHint: 'Enable autostart or unrestricted background for Device Monitor.',
    manualConfirm: true,
  },
  deviceAdmin: {
    id: 'deviceAdmin',
    kind: 'special',
    label: 'Device Admin Permission',
    explanation: 'Device administrator must be deactivated before uninstalling — anti-tampering protection.',
    settingsHint: 'Activate Device Monitor as device administrator.',
  },
  fullScreenIntent: {
    id: 'fullScreenIntent',
    kind: 'special',
    label: 'Full-screen notifications',
    explanation:
      'Android 14+ requires "Allow full-screen intent notifications" so parent live view can start when this app is in the background (especially on MIUI / HyperOS).',
    settingsHint:
      'Settings → Apps → Special app access → Full screen intents → enable for Device Monitor.',
  },
  screenCapture: {
    id: 'screenCapture',
    kind: 'screen',
    label: 'Screen Capture',
    explanation:
      'The system Share screen dialog opens next. With Accessibility enabled, it is approved automatically — no tap needed.',
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
      description: 'Location, microphone, camera, and notifications — tap Allow on each system dialog.',
      permissionIds: [...RUNTIME_PERMISSION_ORDER],
    },
    {
      id: 'usageLimits',
      icon: '🛡️',
      title: 'Usage Limits',
      description: 'Limit screen and app usage time for this device.',
      permissionIds: ['accessibility', 'overlay'],
    },
    {
      id: 'syncNotifications',
      icon: '📢',
      title: 'Sync Notifications',
      description: 'Syncing and viewing app notifications for this device.',
      permissionIds:
        Platform.OS === 'android' && Platform.Version >= 34
          ? (['notificationAccess', 'fullScreenIntent'] as PermissionId[])
          : (['notificationAccess'] as PermissionId[]),
    },
    {
      id: 'usageReport',
      icon: '📊',
      title: 'Usage Report',
      description: 'View screen usage time and app usage for this device.',
      permissionIds: ['usageStats'],
    },
    {
      id: 'background',
      icon: '🔋',
      title: 'Keep running in the background',
      description: 'Grant these so monitoring stays stable in the background.',
      permissionIds: backgroundIds,
    },
    {
      id: 'screenShare',
      icon: '📺',
      title: 'Screen Sharing',
      description: 'One-time screen capture consent for parent live view.',
      permissionIds: ['screenCapture'],
    },
  ];
}
