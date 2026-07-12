import {Platform} from 'react-native';
import {nativeModules} from '../../nativeModules';
import type {PermissionId, PermissionStatusMap} from './types';

export async function loadPermissionStatus(): Promise<PermissionStatusMap> {
  const requiresAutoStart = await nativeModules.requiresOemBackgroundSetup();
  const [
    location,
    microphone,
    camera,
    postNotifications,
    accessibility,
    usageStats,
    notificationAccess,
    overlay,
    battery,
    autoStart,
    deviceAdmin,
    screenCaptureRaw,
  ] = await Promise.all([
    nativeModules.isLocationGranted(),
    nativeModules.isMicGranted(),
    nativeModules.isCameraGranted(),
    Platform.OS === 'android' && Platform.Version >= 33
      ? nativeModules.isPostNotificationsGranted()
      : Promise.resolve(true),
    nativeModules.isAccessibilityEnabled(),
    nativeModules.isUsageStatsGranted(),
    nativeModules.isNotificationListenerEnabled(),
    nativeModules.isOverlayGranted(),
    nativeModules.isBatteryOptimizationExempt(),
    requiresAutoStart ? nativeModules.isOemAutostartPromptDone() : Promise.resolve(true),
    nativeModules.isDeviceAdminActive(),
    nativeModules.isMediaProjectionGranted(),
  ]);

  const screenCapture = screenCaptureRaw && accessibility;

  return {
    location,
    microphone,
    camera,
    postNotifications,
    accessibility,
    usageStats,
    notificationAccess,
    overlay,
    battery,
    autoStart,
    deviceAdmin,
    screenCapture,
  };
}

export function isPermissionStatusComplete(status: PermissionStatusMap): boolean {
  return Object.values(status).every(Boolean);
}

export async function checkPermission(id: PermissionId): Promise<boolean> {
  const status = await loadPermissionStatus();
  return status[id];
}
