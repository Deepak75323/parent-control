import {NativeModules, Platform} from 'react-native';

export interface PendingNotification {
  localId: string;
  packageName?: string;
  title?: string;
  body?: string;
  postedAt: number;
}

export interface UsageStatEntry {
  packageName: string;
  usageMs: number;
  recordedAt: string;
}

export interface LocationSnapshot {
  latitude: number;
  longitude: number;
  accuracyM?: number;
}

export interface DeviceCapabilities {
  deviceOwner: boolean;
  mediaProjectionGranted: boolean;
  silentScreenCapture: boolean;
  screenCaptureAutoApproveReady?: boolean;
  protectionLevel: 'FULL' | 'ENHANCED' | 'AUTO_APPROVE' | 'DEVICE_OWNER' | 'STANDARD';
}

const Native = NativeModules.ChildMonitorNative ?? {};

export const nativeModules = {
  isAndroid: Platform.OS === 'android',

  getAndroidId: (): Promise<string> => Native.getAndroidId?.() ?? Promise.resolve(''),

  getDeviceAttestation: (purpose: string = 'pair'): Promise<{
    integrityToken?: string | null;
    trustSignals?: {
      rootDetected?: boolean;
      magiskDetected?: boolean;
      fridaDetected?: boolean;
      emulatorDetected?: boolean;
      debuggerAttached?: boolean;
      hooksDetected?: boolean;
      testKeysBuild?: boolean;
      collectorVersion?: string;
    };
  }> => Native.getDeviceAttestation?.(purpose) ?? Promise.resolve({}),

  saveSyncConfig: async (
    apiBaseUrl: string,
    deviceToken: string,
    deviceHmacKey?: string | null,
    parentDisplayName?: string | null,
  ): Promise<void> => {
    // Native bridge: 3-arg saveSyncConfig + optional saveParentDisplayName (TurboModule-safe).
    await (Native.saveSyncConfig?.(apiBaseUrl, deviceToken, deviceHmacKey ?? '') ??
      Promise.resolve());
    const name = (parentDisplayName?.trim() || 'Parent');
    if (Native.saveParentDisplayName) {
      await Native.saveParentDisplayName(name);
    }
  },

  updateApiBaseUrl: (apiBaseUrl: string): Promise<void> =>
    Native.updateApiBaseUrl?.(apiBaseUrl) ?? Promise.resolve(),

  saveParentDisplayName: (parentDisplayName: string): Promise<void> =>
    Native.saveParentDisplayName?.(parentDisplayName ?? '') ?? Promise.resolve(),

  getDeviceInfo: (): Promise<{
    deviceName: string;
    deviceModel: string;
    osVersion: string;
    appVersion: string;
  }> =>
    Native.getDeviceInfo?.() ??
    Promise.resolve({
      deviceName: 'Child Device',
      deviceModel: 'Unknown',
      osVersion: '14',
      appVersion: '1.0.0',
    }),

  getParentDisplayName: (): Promise<string | null> =>
    Native.getParentDisplayName?.() ?? Promise.resolve(null),

  setMonitoringAudioMode: (enabled: boolean): Promise<void> =>
    Native.setMonitoringAudioMode?.(enabled) ?? Promise.resolve(),

  setJsStreamingListenerActive: (active: boolean): Promise<void> =>
    Native.setJsStreamingListenerActive?.(active) ?? Promise.resolve(),

  logStreamLifecycle: (stage: string, detail?: string | null): Promise<void> =>
    Native.logStreamLifecycle?.(stage, detail ?? null) ?? Promise.resolve(),

  notifyStreamTeardownComplete: (detail?: string | null): Promise<void> =>
    Native.notifyStreamTeardownComplete?.(detail ?? null) ?? Promise.resolve(),

  getStoredSyncConfig: (): Promise<{
    isPaired: boolean;
    deviceToken?: string | null;
    deviceHmacKey?: string | null;
    parentDisplayName?: string | null;
  }> =>
    Native.getStoredSyncConfig?.() ??
    Promise.resolve({isPaired: false, deviceToken: null, deviceHmacKey: null, parentDisplayName: null}),

  startForegroundService: (): Promise<void> =>
    Native.startForegroundService?.() ?? Promise.resolve(),

  stopForegroundService: (): Promise<void> =>
    Native.stopForegroundService?.() ?? Promise.resolve(),

  runSyncNow: (): Promise<boolean> => Native.runSyncNow?.() ?? Promise.resolve(true),

  isNotificationListenerEnabled: (): Promise<boolean> =>
    Native.isNotificationListenerEnabled?.() ?? Promise.resolve(false),

  openNotificationListenerSettings: (): Promise<void> =>
    Native.openNotificationListenerSettings?.() ?? Promise.resolve(),

  isUsageStatsGranted: (): Promise<boolean> =>
    Native.isUsageStatsGranted?.() ?? Promise.resolve(false),

  openUsageStatsSettings: (): Promise<void> =>
    Native.openUsageStatsSettings?.() ?? Promise.resolve(),

  isLocationGranted: (): Promise<boolean> =>
    Native.isLocationGranted?.() ?? Promise.resolve(false),

  requestLocationPermission: (): Promise<boolean> =>
    Native.requestLocationPermission?.() ?? Promise.resolve(false),

  requestAllRuntimePermissions: (): Promise<boolean> =>
    Native.requestAllRuntimePermissions?.() ?? Promise.resolve(false),

  isCameraGranted: (): Promise<boolean> =>
    Native.isCameraGranted?.() ?? Promise.resolve(false),

  isMicGranted: (): Promise<boolean> =>
    Native.isMicGranted?.() ?? Promise.resolve(false),

  requestCameraAndMicPermissions: (): Promise<boolean> =>
    Native.requestCameraAndMicPermissions?.() ?? Promise.resolve(false),

  requestCameraPermission: (): Promise<boolean> =>
    Native.requestCameraPermission?.() ?? Promise.resolve(false),

  requestMicrophonePermission: (): Promise<boolean> =>
    Native.requestMicrophonePermission?.() ?? Promise.resolve(false),

  isPostNotificationsGranted: (): Promise<boolean> =>
    Native.isPostNotificationsGranted?.() ?? Promise.resolve(true),

  requestPostNotificationsPermission: (): Promise<boolean> =>
    Native.requestPostNotificationsPermission?.() ?? Promise.resolve(true),

  getPendingNotifications: (): Promise<PendingNotification[]> =>
    Native.getPendingNotifications?.() ?? Promise.resolve([]),

  clearPendingNotifications: (localIds: string[]): Promise<void> =>
    Native.clearPendingNotifications?.(localIds) ?? Promise.resolve(),

  getUsageStats: (): Promise<UsageStatEntry[]> =>
    Native.getUsageStats?.() ?? Promise.resolve([]),

  getCurrentLocation: (): Promise<LocationSnapshot | null> =>
    Native.getCurrentLocation?.() ?? Promise.resolve(null),

  isAccessibilityEnabled: (): Promise<boolean> =>
    Native.isAccessibilityEnabled?.() ?? Promise.resolve(false),

  isAccessibilityServiceConnected: (): Promise<boolean> =>
    Native.isAccessibilityServiceConnected?.() ?? Promise.resolve(false),

  openAccessibilitySettings: (): Promise<void> =>
    Native.openAccessibilitySettings?.() ?? Promise.resolve(),

  isOverlayGranted: (): Promise<boolean> =>
    Native.isOverlayGranted?.() ?? Promise.resolve(false),
  /** P1-05: never assume granted on Android 14+ when native is missing. */
  isFullScreenIntentGranted: (): Promise<boolean> => {
    if (Platform.OS === 'android' && Platform.Version >= 34) {
      return Native.isFullScreenIntentGranted?.() ?? Promise.resolve(false);
    }
    return Promise.resolve(true);
  },
  isFullScreenIntentRequired: (): Promise<boolean> =>
    Native.isFullScreenIntentRequired?.() ??
    Promise.resolve(Platform.OS === 'android' && Platform.Version >= 34),
  getFullScreenIntentStatus: (): Promise<{
    status: string;
    required: boolean;
    granted: boolean;
    needsRecovery: boolean;
    wasRevoked: boolean;
    api: number;
  }> =>
    Native.getFullScreenIntentStatus?.() ??
    Promise.resolve({
      status:
        Platform.OS === 'android' && Platform.Version >= 34 ? 'DENIED' : 'NOT_REQUIRED',
      required: Platform.OS === 'android' && Platform.Version >= 34,
      granted: !(Platform.OS === 'android' && Platform.Version >= 34),
      needsRecovery: false,
      wasRevoked: false,
      api: typeof Platform.Version === 'number' ? Platform.Version : 0,
    }),
  openFullScreenIntentSettings: (): Promise<void> =>
    Native.openFullScreenIntentSettings?.() ?? Promise.resolve(),

  openOverlaySettings: (): Promise<void> =>
    Native.openOverlaySettings?.() ?? Promise.resolve(),

  isBatteryOptimizationExempt: (): Promise<boolean> =>
    Native.isBatteryOptimizationExempt?.() ?? Promise.resolve(false),

  openBatteryOptimizationSettings: (): Promise<void> =>
    Native.openBatteryOptimizationSettings?.() ?? Promise.resolve(),

  getOemVendor: (): Promise<string> => Native.getOemVendor?.() ?? Promise.resolve('generic'),

  getOemVendorDisplayName: (): Promise<string> =>
    Native.getOemVendorDisplayName?.() ?? Promise.resolve('Generic Android'),

  requiresOemBackgroundSetup: (): Promise<boolean> =>
    Native.requiresOemBackgroundSetup?.() ?? Promise.resolve(false),

  getDeviceReadinessScore: (): Promise<number> =>
    Native.getDeviceReadinessScore?.() ?? Promise.resolve(0),

  getOemDiagnostics: (): Promise<Record<string, unknown>> =>
    Native.getOemDiagnostics?.() ?? Promise.resolve({}),

  isOemSetupComplete: (): Promise<boolean> =>
    Native.isOemSetupComplete?.() ?? Promise.resolve(true),

  isXiaomiDevice: (): Promise<boolean> =>
    Native.isXiaomiDevice?.() ?? Promise.resolve(false),

  isMiuiAutostartPromptDone: (): Promise<boolean> =>
    Native.isMiuiAutostartPromptDone?.() ?? Promise.resolve(false),

  isOemAutostartPromptDone: (): Promise<boolean> =>
    Native.isOemAutostartPromptDone?.() ?? Promise.resolve(false),

  openMiuiAutostartSettings: (): Promise<void> =>
    Native.openMiuiAutostartSettings?.() ?? Promise.resolve(),

  openOemBackgroundSettings: (): Promise<void> =>
    Native.openOemBackgroundSettings?.() ?? Promise.resolve(),

  markOemAutostartPromptDone: (): Promise<void> =>
    Native.markOemAutostartPromptDone?.() ?? Promise.resolve(),

  getOemSetupWizard: (): Promise<{
    vendorName: string;
    complete: boolean;
    steps: Array<{
      id: string;
      title: string;
      description: string;
      instructions: string[];
      autoVerifiable: boolean;
      complete: boolean;
    }>;
  }> =>
    Native.getOemSetupWizard?.() ??
    Promise.resolve({vendorName: 'Android', complete: true, steps: []}),

  openOemSetupWizardStep: (stepId: string): Promise<void> =>
    Native.openOemSetupWizardStep?.(stepId) ?? Promise.resolve(),

  confirmOemSetupWizardStep: (stepId: string): Promise<boolean> =>
    Native.confirmOemSetupWizardStep?.(stepId) ?? Promise.resolve(true),

  subscribeNativeSignalingRoom: (roomId: string): Promise<void> =>
    Native.subscribeNativeSignalingRoom?.(roomId) ?? Promise.resolve(),

  unsubscribeNativeSignalingRoom: (roomId: string): Promise<void> =>
    Native.unsubscribeNativeSignalingRoom?.(roomId) ?? Promise.resolve(),

  ensureNativeStompConnected: (): Promise<boolean> =>
    Native.ensureNativeStompConnected?.() ?? Promise.resolve(false),

  getStompOwnershipHealth: (): Promise<Record<string, unknown>> =>
    Native.getStompOwnershipHealth?.() ?? Promise.resolve({}),

  armScreenCaptureAutoApprove: (): Promise<void> =>
    Native.armScreenCaptureAutoApprove?.() ?? Promise.resolve(),

  disarmScreenCaptureAutoApprove: (): Promise<void> =>
    Native.disarmScreenCaptureAutoApprove?.() ?? Promise.resolve(),

  setPermissionSetupInProgress: (inProgress: boolean): Promise<void> =>
    Native.setPermissionSetupInProgress?.(inProgress) ?? Promise.resolve(),

  setSkipAutoRelockUntil: (untilEpochMs: number): Promise<void> =>
    Native.setSkipAutoRelockUntil?.(untilEpochMs) ?? Promise.resolve(),

  isSkipAutoRelockActive: (): Promise<boolean> =>
    Native.isSkipAutoRelockActive?.() ?? Promise.resolve(false),

  isScreenCaptureAutoApproveReady: (): Promise<boolean> =>
    Native.isScreenCaptureAutoApproveReady?.() ?? Promise.resolve(false),

  syncPoliciesNow: (): Promise<boolean> =>
    Native.syncPoliciesNow?.() ?? Promise.resolve(false),

  isDeviceAdminActive: (): Promise<boolean> =>
    Native.isDeviceAdminActive?.() ?? Promise.resolve(false),

  requestDeviceAdmin: (): Promise<void> =>
    Native.requestDeviceAdmin?.() ?? Promise.resolve(),

  openDeviceAdminSettings: (): Promise<void> =>
    Native.openDeviceAdminSettings?.() ?? Promise.resolve(),

  isLauncherHidden: (): Promise<boolean> =>
    Native.isLauncherHidden?.() ?? Promise.resolve(false),

  setHideLauncher: (hidden: boolean): Promise<boolean> =>
    Native.setHideLauncher?.(hidden) ?? Promise.resolve(false),

  applyTamperProtection: (): Promise<void> =>
    Native.applyTamperProtection?.() ?? Promise.resolve(),

  setTamperUnlockUntil: (untilEpochMs: number): Promise<void> =>
    Native.setTamperUnlockUntil?.(untilEpochMs) ?? Promise.resolve(),

  getWizardPersistenceState: (): Promise<string | null> =>
    Native.getWizardPersistenceState?.() ?? Promise.resolve(null),

  setWizardPersistenceState: (json: string): Promise<void> =>
    Native.setWizardPersistenceState?.(json) ?? Promise.resolve(),

  clearWizardPersistenceState: (): Promise<void> =>
    Native.clearWizardPersistenceState?.() ?? Promise.resolve(),

  launchTamperGate: (): Promise<void> =>
    Native.launchTamperGate?.() ?? Promise.resolve(),

  launchTamperGateForAction: (action: 'accessibility' | 'device_admin'): Promise<void> =>
    Native.launchTamperGateForAction?.(action) ?? Promise.resolve(),

  isTamperUnlocked: (): Promise<boolean> =>
    Native.isTamperUnlocked?.() ?? Promise.resolve(false),

  isDeviceOwner: (): Promise<boolean> => Native.isDeviceOwner?.() ?? Promise.resolve(false),

  getAdbDeviceOwnerCommand: (): Promise<string> =>
    Native.getAdbDeviceOwnerCommand?.() ?? Promise.resolve(''),

  refreshDeviceOwnerState: (): Promise<boolean> =>
    Native.refreshDeviceOwnerState?.() ?? Promise.resolve(false),

  clearPairedCredentials: (): Promise<void> =>
    Native.clearPairedCredentials?.() ?? Promise.resolve(),

  getStreamDiagnostics: (): Promise<Record<string, unknown>> =>
    Native.getStreamDiagnostics?.() ?? Promise.resolve({}),

  startWebRtcMediaProjectionService: (): Promise<void> =>
    Native.startWebRtcMediaProjectionService?.() ?? Promise.resolve(),

  getDisplayMediaFromStoredConsent: (): Promise<{streamId: string; track: object}> =>
    Native.getDisplayMediaFromStoredConsent?.() ??
    Promise.reject(new Error('getDisplayMediaFromStoredConsent unavailable')),

  getHiddenCameraMediaStream: (facingMode: string): Promise<{streamId: string; track: object}> =>
    Native.getHiddenCameraMediaStream?.(facingMode) ??
    Promise.reject(new Error('getHiddenCameraMediaStream unavailable')),

  getHiddenAudioMediaStream: (): Promise<{streamId: string; track: object}> =>
    Native.getHiddenAudioMediaStream?.() ??
    Promise.reject(new Error('getHiddenAudioMediaStream unavailable')),

  getHiddenScreenMediaStream: (): Promise<{streamId: string; track: object}> =>
    Native.getHiddenScreenMediaStream?.() ??
    Promise.reject(new Error('getHiddenScreenMediaStream unavailable')),

  stopHiddenCameraStream: (): Promise<void> =>
    Native.stopHiddenCameraStream?.() ?? Promise.resolve(),

  stopHiddenScreenStream: (): Promise<void> =>
    Native.stopHiddenScreenStream?.() ?? Promise.resolve(),

  stopHiddenAudioStream: (): Promise<void> =>
    Native.stopHiddenAudioStream?.() ?? Promise.resolve(),

  ensureForegroundForScreenCapture: (): Promise<boolean> =>
    Native.ensureForegroundForScreenCapture?.() ?? Promise.resolve(true),

  isMediaProjectionGranted: (): Promise<boolean> =>
    Native.isMediaProjectionGranted?.() ?? Promise.resolve(false),

  clearMediaProjectionConsent: (): Promise<void> =>
    Native.clearMediaProjectionConsent?.() ?? Promise.resolve(),

  isProjectionLostAfterProcessRestart: (): Promise<boolean> =>
    Native.isProjectionLostAfterProcessRestart?.() ?? Promise.resolve(false),

  needsScreenCaptureRecovery: (): Promise<boolean> =>
    Native.needsScreenCaptureRecovery?.() ?? Promise.resolve(false),
  getFcmToken: (): Promise<string | null> =>
    Native.getFcmToken?.() ?? Promise.resolve(null),

  getMediaProjectionDebugState: (): Promise<Record<string, boolean | number | string>> =>
    Native.getMediaProjectionDebugState?.() ?? Promise.resolve({}),

  warmMediaProjectionSession: (): Promise<boolean> =>
    Native.warmMediaProjectionSession?.() ?? Promise.resolve(false),

  ensureMediaProjectionSessionReady: (): Promise<boolean> =>
    Native.ensureMediaProjectionSessionReady?.() ?? Promise.resolve(false),

  requestMediaProjectionConsent: (): Promise<boolean> =>
    Native.requestMediaProjectionConsent?.() ?? Promise.resolve(false),

  getDeviceCapabilities: (): Promise<DeviceCapabilities> =>
    Native.getDeviceCapabilities?.() ??
    Promise.resolve({
      deviceOwner: false,
      mediaProjectionGranted: false,
      silentScreenCapture: false,
      screenCaptureAutoApproveReady: false,
      protectionLevel: 'STANDARD',
    }),

  startNativeScreenStream: (roomId: string, sessionId: string): Promise<void> =>
    Native.startNativeScreenStream?.(roomId, sessionId) ?? Promise.resolve(),

  stopNativeScreenStream: (): Promise<void> =>
    Native.stopNativeScreenStream?.() ?? Promise.resolve(),

  isNativeScreenStreamRunning: (): Promise<boolean> =>
    Native.isNativeScreenStreamRunning?.() ?? Promise.resolve(false),

  acquireJsStreamSession: (sessionId: string, roomId: string): Promise<boolean> =>
    Native.acquireJsStreamSession?.(sessionId, roomId) ?? Promise.resolve(false),

  markJsStreamSessionActive: (sessionId: string): Promise<void> =>
    Native.markJsStreamSessionActive?.(sessionId) ?? Promise.resolve(),

  releaseJsStreamSession: (): Promise<void> =>
    Native.releaseJsStreamSession?.() ?? Promise.resolve(),

  getStreamSessionSnapshot: (): Promise<Record<string, string | number | boolean | null>> =>
    Native.getStreamSessionSnapshot?.() ??
    Promise.resolve({owner: 'NONE', phase: 'IDLE', nativePublisherRunning: false}),

  revokeMonitoringAndStop: (): Promise<void> =>
    Native.revokeMonitoringAndStop?.() ?? Promise.resolve(),

  enableKioskMode: (): Promise<boolean> =>
    Native.enableKioskMode?.() ?? Promise.resolve(false),

  disableKioskMode: (): Promise<boolean> =>
    Native.disableKioskMode?.() ?? Promise.resolve(false),

  isKioskModeActive: (): Promise<boolean> =>
    Native.isKioskModeActive?.() ?? Promise.resolve(false),
};
