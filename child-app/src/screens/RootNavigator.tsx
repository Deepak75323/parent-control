import React, {createContext, useContext, useEffect, useState} from 'react';
import {AppState, AppStateStatus} from 'react-native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {SetupWizardScreen} from './SetupWizardScreen';
import {PermissionSetupScreen} from './PermissionSetupScreen';
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
import {flowLog} from '../services/flowLogger';
import {slog} from '../services/streamDebug';

export type ChildStackParamList = {
  SetupWizard: undefined;
  PermissionSetup: undefined;
  OemSetupWizard: undefined;
  DeviceOwnerSetup: undefined;
  Pairing: undefined;
  ServiceStatus: undefined;
  PinVerification: undefined;
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
    resolveApiBaseUrl()
      .catch(() => undefined)
      .then(() => restoreDeviceCredentials())
      .then(async token => {
        slog('APP', 'restoreDeviceCredentials', {paired: !!token});
        if (token) {
          await resumePairedSession(token);
        } else {
          resetChildNavigation('SetupWizard');
        }
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
        if (isSetupOrPairingRoute(route)) {
          // Pairing saves credentials before the permissions step finishes — do not
          // yank the user off Pairing/PermissionSetup when returning from a settings screen.
          flowLog('SCREEN_SHARE_FLOW', 'AppState active — skip ServiceStatus reset during setup', {
            route,
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
    resetChildNavigation('SetupWizard');
    await clearDeviceCredentials();
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
        initialRouteName="SetupWizard"
        screenOptions={{
          headerShown: false,
          contentStyle: {flex: 1, backgroundColor: '#f1f5f9'},
        }}>
        <Stack.Screen name="SetupWizard" component={SetupWizardScreen} />
        <Stack.Screen
          name="PermissionSetup"
          component={PermissionSetupScreen}
          options={{gestureEnabled: false, fullScreenGestureEnabled: false}}
        />
        <Stack.Screen
          name="OemSetupWizard"
          component={OemSetupWizardScreen}
          options={{gestureEnabled: false, fullScreenGestureEnabled: false}}
        />
        <Stack.Screen name="DeviceOwnerSetup" component={DeviceOwnerSetupScreen} />
        <Stack.Screen name="Pairing" component={PairingScreen} />
        <Stack.Screen name="ServiceStatus" component={ServiceStatusScreen} />
        <Stack.Screen name="PinVerification" component={PinVerificationScreen} />
        <Stack.Screen name="CheckPermissions" component={CheckPermissionsScreen} />
        <Stack.Screen name="StreamIncoming" component={StreamIncomingScreen} />
        <Stack.Screen name="DebugDashboard" component={DebugDashboardScreen} />
      </Stack.Navigator>
    </PairingCompleteContext.Provider>
  );
}
