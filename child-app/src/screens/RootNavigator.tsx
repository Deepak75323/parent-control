import React, {createContext, useContext, useEffect, useState} from 'react';
import {AppState, AppStateStatus} from 'react-native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {ChildLoginScreen} from './ChildLoginScreen';
import {ChildRecoveryScreen} from './ChildRecoveryScreen';
import {PermissionWizardScreen} from './PermissionWizardScreen';
import {PermissionDashboardScreen} from './PermissionDashboardScreen';
import {DeviceOwnerSetupScreen} from './DeviceOwnerSetupScreen';
import {OemSetupWizardScreen} from './OemSetupWizardScreen';
import {PairingScreen} from './PairingScreen';
import {ServiceStatusScreen} from './ServiceStatusScreen';
import {PinVerificationScreen} from './PinVerificationScreen';
import {CheckPermissionsScreen} from './CheckPermissionsScreen';
import {StreamIncomingScreen} from './StreamIncomingScreen';
import {DebugDashboardScreen} from './DebugDashboardScreen';
import {LoadingCenter} from '../components/AppScreen';
import {
  restoreDeviceCredentials,
  getDeviceHmacKey,
  clearDeviceCredentials,
  getParentDisplayName,
} from '../services/deviceStorage';
import {startMonitoringSync, stopMonitoringSync} from '../services/syncService';
import {pauseStreamingListener} from '../services/streamingService';
import {isStreamActive} from '../services/webrtcService';
import {
  getCurrentChildRoute,
  isSetupOrPairingRoute,
  resetChildNavigation,
} from '../navigation/navigationRef';
import {resolveApiBaseUrl} from '../utils/config';
import {nativeModules} from '../nativeModules';
import {flowLog, slog} from '../services/flowLogger';
import {
  clearOnboardingSession,
  isOnboardingAuthenticated,
} from '../services/onboarding/onboardingSession';
import {subscribeMonitoringRevoked} from '../services/monitoringRevokeHandler';

export type ChildStackParamList = {
  ChildLogin: {revokedByParent?: boolean} | undefined;
  ChildRecovery: undefined;
  PermissionWizard: undefined;
  PermissionDashboard: undefined;
  OemSetupWizard: undefined;
  DeviceOwnerSetup: undefined;
  Pairing: undefined;
  ServiceStatus: undefined;
  PinVerification: undefined;
  CheckPermissions: undefined;
  StreamIncoming: undefined;
  DebugDashboard: undefined;
};

const Stack = createNativeStackNavigator<ChildStackParamList>();

type PairingContextValue = {
  markPaired: (parentDisplayName?: string) => void;
  markUnpaired: () => Promise<void>;
  markRePairing: () => Promise<void>;
  pairedParentDisplayName: string | null;
};

const PairingCompleteContext = createContext<PairingContextValue | null>(null);

export function usePairingComplete() {
  const ctx = useContext(PairingCompleteContext);
  if (!ctx) {
    throw new Error('usePairingComplete must be used within RootNavigator');
  }
  return ctx;
}

async function resolveInitialRoute(): Promise<keyof ChildStackParamList> {
  const authenticated = await isOnboardingAuthenticated();
  if (!authenticated) {
    return 'ChildLogin';
  }
  // Skip heavy native permission probes during boot — wizard re-checks each step.
  const token = await restoreDeviceCredentials();
  if (token) {
    return 'ServiceStatus';
  }
  if (authenticated) {
    return 'PermissionWizard';
  }
  return 'Pairing';
}

export function RootNavigator() {
  const [bootstrapped, setBootstrapped] = useState(false);
  const [pairedParentDisplayName, setPairedParentDisplayName] = useState<string | null>(null);

  const resumePairedSession = async (token: string) => {
    const hmacKey = await getDeviceHmacKey();
    const name = await getParentDisplayName();
    if (name) {
      setPairedParentDisplayName(name);
    }
    resetChildNavigation('ServiceStatus');
    await startMonitoringSync(token, hmacKey ?? undefined, name ?? undefined);
  };

  useEffect(() => {
    const unsubscribeRevoke = subscribeMonitoringRevoked();
    return unsubscribeRevoke;
  }, []);

  useEffect(() => {
    void resolveApiBaseUrl().catch(() => undefined);
    resolveInitialRoute()
      .then(async route => {
        slog('APP', 'boot route', {route});
        if (route === 'PermissionWizard') {
          await nativeModules.setPermissionSetupInProgress(true);
        }
        if (route === 'ServiceStatus') {
          const token = await restoreDeviceCredentials();
          if (token) {
            await resumePairedSession(token);
            return;
          }
        }
        resetChildNavigation(route);
      })
      .finally(() => setBootstrapped(true));
  }, []);

  useEffect(() => {
    const onAppStateChange = (nextState: AppStateStatus) => {
      if (nextState === 'background' || nextState === 'inactive') {
        void nativeModules.logStreamLifecycle('APP_BACKGROUND', `AppState=${nextState}`);
        flowLog('SCREEN_SHARE_FLOW', 'AppState background — pause JS listener, keep capture FGS', {
          state: nextState,
        });
        pauseStreamingListener();
        if (!isStreamActive()) {
          void nativeModules.setJsStreamingListenerActive(false);
        }
        return;
      }
      if (nextState !== 'active') return;
      void nativeModules.logStreamLifecycle('APP_FOREGROUND', `AppState=${nextState}`);
      flowLog('SCREEN_SHARE_FLOW', 'AppState active — JS streaming listener will resume', {
        state: nextState,
      });
      restoreDeviceCredentials().then(async token => {
        if (!token) return;
        const route = getCurrentChildRoute();
        if (!route || isSetupOrPairingRoute(route)) {
          flowLog('SCREEN_SHARE_FLOW', 'AppState active — skip ServiceStatus reset during setup', {
            route: route ?? 'unknown',
          });
          return;
        }
        const name = await getParentDisplayName();
        if (name) {
          setPairedParentDisplayName(name);
        }
        resetChildNavigation('ServiceStatus');
        const hmacKey = await getDeviceHmacKey();
        await startMonitoringSync(token, hmacKey ?? undefined, name ?? undefined);
      });
    };
    const subscription = AppState.addEventListener('change', onAppStateChange);
    return () => subscription.remove();
  }, []);

  const markPaired = (parentDisplayName?: string) => {
    if (parentDisplayName?.trim()) {
      setPairedParentDisplayName(parentDisplayName.trim());
    }
    resetChildNavigation('ServiceStatus');
  };

  const markUnpaired = async () => {
    setPairedParentDisplayName(null);
    stopMonitoringSync();
    await clearDeviceCredentials();
    await clearOnboardingSession();
    resetChildNavigation('ChildLogin');
  };

  const markRePairing = async () => {
    setPairedParentDisplayName(null);
    stopMonitoringSync();
    await clearDeviceCredentials();
    resetChildNavigation('Pairing');
  };

  if (!bootstrapped) {
    return <LoadingCenter label="Starting device monitor…" />;
  }

  return (
    <PairingCompleteContext.Provider
      value={{markPaired, markUnpaired, markRePairing, pairedParentDisplayName}}>
      <Stack.Navigator
        initialRouteName="ChildLogin"
        screenOptions={{
          headerShown: false,
          contentStyle: {flex: 1, backgroundColor: '#f1f5f9'},
        }}>
        <Stack.Screen
          name="ChildLogin"
          component={ChildLoginScreen}
          options={{gestureEnabled: false, fullScreenGestureEnabled: false}}
        />
        <Stack.Screen
          name="ChildRecovery"
          component={ChildRecoveryScreen}
          options={{gestureEnabled: false, fullScreenGestureEnabled: false}}
        />
        <Stack.Screen
          name="PermissionWizard"
          component={PermissionWizardScreen}
          options={{gestureEnabled: false, fullScreenGestureEnabled: false}}
        />
        <Stack.Screen
          name="PermissionDashboard"
          component={PermissionDashboardScreen}
          options={{gestureEnabled: false, fullScreenGestureEnabled: false}}
        />
        <Stack.Screen
          name="OemSetupWizard"
          component={OemSetupWizardScreen}
          options={{gestureEnabled: false, fullScreenGestureEnabled: false}}
        />
        <Stack.Screen name="DeviceOwnerSetup" component={DeviceOwnerSetupScreen} />
        <Stack.Screen
          name="Pairing"
          component={PairingScreen}
          options={{gestureEnabled: false, fullScreenGestureEnabled: false}}
        />
        <Stack.Screen name="ServiceStatus" component={ServiceStatusScreen} />
        <Stack.Screen name="PinVerification" component={PinVerificationScreen} />
        <Stack.Screen name="CheckPermissions" component={CheckPermissionsScreen} />
        <Stack.Screen name="StreamIncoming" component={StreamIncomingScreen} />
        <Stack.Screen name="DebugDashboard" component={DebugDashboardScreen} />
      </Stack.Navigator>
    </PairingCompleteContext.Provider>
  );
}
