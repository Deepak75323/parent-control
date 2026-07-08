import {NativeEventEmitter, NativeModules, Platform} from 'react-native';
import {resetChildNavigation} from '../navigation/navigationRef';
import {stopMonitoringSync} from './syncService';
import {disconnectStream} from './webrtcService';
import {clearDeviceCredentials} from './deviceStorage';
import {clearOnboardingSession} from './onboarding/onboardingSession';
import {cancelActivePermissionGrant} from './permissionDashboard/grantOrchestrator';
import {flowLog} from './flowLogger';

let subscription: {remove: () => void} | null = null;
let handling = false;

/** Tear down JS monitoring state and navigate to Child Login immediately. */
export async function handleMonitoringRevoked(source?: string): Promise<void> {
  if (handling) {
    flowLog('REVOKE', 'handleMonitoringRevoked coalesced', {source});
    return;
  }
  handling = true;
  try {
    flowLog('REVOKE', 'handleMonitoringRevoked', {source});
    cancelActivePermissionGrant();
    stopMonitoringSync();
    await disconnectStream().catch(() => undefined);
    await clearDeviceCredentials();
    await clearOnboardingSession();
    resetChildNavigation('ChildLogin', {revokedByParent: true});
  } finally {
    handling = false;
  }
}

/** Subscribe to native `monitoringRevoked` events for the app lifetime. */
export function subscribeMonitoringRevoked(): () => void {
  if (subscription || Platform.OS !== 'android') {
    return () => subscription?.remove();
  }
  const emitter = new NativeEventEmitter(NativeModules.ChildMonitorNative);
  subscription = emitter.addListener(
    'monitoringRevoked',
    (event: {source?: string}) => {
      void handleMonitoringRevoked(event?.source);
    },
  );
  return () => {
    subscription?.remove();
    subscription = null;
  };
}
