import {Platform} from 'react-native';
import {nativeModules} from '../../nativeModules';
import type {PermissionId} from '../permissionDashboard/types';

/** Verify real permission state — do not rely on activity results alone. */
export async function isPermissionGranted(id: PermissionId): Promise<boolean> {
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
