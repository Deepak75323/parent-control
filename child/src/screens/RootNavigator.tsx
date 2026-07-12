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
import {pauseStreamingListener, startStreamingListener} from '../services/streamingService';
import {isStreamActive, isStreamConnecting} from '../services/webrtcService';
import {isAutoLockSuppressed, isAutoLockSuppressedAsync} from '../services/autoLockGuard';
import {
  getCurrentChildRoute,
  isSetupOrPairingRoute,
  resetChildNavigation,
} from '../navigation/navigationRef';
import {resolveApiBaseUrl} from '../utils/config';
import {nativeModules} from '../nativeModules';
import {flowLog, slog} from '../services/flowLogger';
import {
  getNavStackSnapshot,
  navTrace,
  navTraceAuth,
  navTraceLifecycle,
  navTraceObserver,
  navTraceTransition,
} from '../navigation/navTrace';
import {
  clearOnboardingSession,
  isOnboardingAuthenticated,
} from '../services/onboarding/onboardingSession';
import {subscribeMonitoringRevoked} from '../services/monitoringRevokeHandler';

export type ChildStackParamList = {
  /** returnTo: when set, successful login navigates to that screen instead of PermissionWizard */
  ChildLogin: {revokedByParent?: boolean; returnTo?: 'ServiceStatus'} | undefined;
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
  const token = await restoreDeviceCredentials();
  navTraceAuth('resolveInitialRoute', {
    onboardingAuthenticated: authenticated,
    tokenExists: !!token,
    authenticated: authenticated || !!token,
  });
  if (!authenticated) {
    navTrace('Boot route resolved', {to: 'ChildLogin', reason: 'not_onboarding_authenticated'});
    return 'ChildLogin';
  }
  // Skip heavy native permission probes during boot — wizard re-checks each step.
  if (token) {
    navTrace('Boot route resolved', {to: 'ChildLogin', reason: 'paired_token_exists_show_lock'});
    // Paired — show the password lock screen first; success navigates to ServiceStatus.
    return 'ChildLogin';
  }
  if (authenticated) {
    navTrace('Boot route resolved', {to: 'PermissionWizard', reason: 'authenticated_unpaired'});
    return 'PermissionWizard';
  }
  navTrace('Boot route resolved', {to: 'Pairing', reason: 'fallback'});
  return 'Pairing';
}

export function RootNavigator() {
  const [bootstrapped, setBootstrapped] = useState(false);
  const [pairedParentDisplayName, setPairedParentDisplayName] = useState<string | null>(null);

  const resumePairedSession = async (token: string) => {
    navTraceTransition(getNavStackSnapshot().active, 'ChildLogin', 'resumePairedSession', 'RootNavigator.resumePairedSession', {
      returnTo: 'ServiceStatus',
      tokenExists: true,
    });
    const hmacKey = await getDeviceHmacKey();
    const name = await getParentDisplayName();
    if (name) {
      setPairedParentDisplayName(name);
    }
    // Show the password lock screen; on success it navigates to ServiceStatus.
    resetChildNavigation('ChildLogin', {returnTo: 'ServiceStatus'});
    await startMonitoringSync(token, hmacKey ?? undefined, name ?? undefined);
  };

  useEffect(() => {
    const unsubscribeRevoke = subscribeMonitoringRevoked();
    return unsubscribeRevoke;
  }, []);

  useEffect(() => {
    navTraceLifecycle('RootNavigator', 'onCreate', {phase: 'boot'});
    void resolveApiBaseUrl().catch(() => undefined);
    resolveInitialRoute()
      .then(async route => {
        slog('APP', 'boot route', {route});
        navTraceTransition('BOOT', route, 'resolveInitialRoute complete', 'RootNavigator.boot');
        if (route === 'PermissionWizard') {
          await nativeModules.setPermissionSetupInProgress(true);
        }
        if (route === 'ChildLogin') {
          const token = await restoreDeviceCredentials();
          navTraceAuth('boot ChildLogin branch', {tokenExists: !!token});
          if (token) {
            const suppressed = await isAutoLockSuppressedAsync();
            navTraceObserver('RootNavigator', 'isAutoLockSuppressedAsync', null, suppressed, false, {
              bootBranch: 'ChildLogin_with_token',
            });
            if (suppressed) {
              flowLog('SCREEN_SHARE_FLOW', 'boot — skip re-lock after consent/system-ui return');
              const hmacKey = await getDeviceHmacKey();
              const name = await getParentDisplayName();
              if (name) {
                setPairedParentDisplayName(name);
              }
              navTraceTransition('ChildLogin', 'ServiceStatus', 'skip re-lock — autoLock suppressed', 'RootNavigator.boot');
              resetChildNavigation('ServiceStatus');
              await startMonitoringSync(token, hmacKey ?? undefined, name ?? undefined);
              return;
            }
            navTraceTransition('BOOT', 'ChildLogin', 'resume paired session (lock screen)', 'RootNavigator.boot');
            await resumePairedSession(token);
            return;
          }
        }
        navTraceTransition('BOOT', route, 'initial resetChildNavigation', 'RootNavigator.boot');
        resetChildNavigation(route);
      })
      .finally(() => {
        navTrace('Boot complete — setBootstrapped(true)', {screen: 'RootNavigator'});
        setBootstrapped(true);
      });
  }, []);

  useEffect(() => {
    const onAppStateChange = (nextState: AppStateStatus) => {
      navTraceObserver('RootNavigator', 'AppState.addEventListener', null, nextState, false, {
        lifecycleCallback: 'AppState.change',
      });
      if (nextState === 'background' || nextState === 'inactive') {
        void nativeModules.logStreamLifecycle('APP_BACKGROUND', `AppState=${nextState}`);
        flowLog('SCREEN_SHARE_FLOW', 'AppState background — pause JS listener, keep capture FGS', {
          state: nextState,
        });
        pauseStreamingListener();
        // Always tell native the JS listener is inactive when backgrounded.
        // Previously this was skipped when isStreamActive() was true, but that left
        // jsListenerActive=true while the streamRequestWake handler was already
        // detached — native would emit wake events into a void instead of routing
        // to the headless path, so background camera requests were silently dropped.
        void nativeModules.setJsStreamingListenerActive(false);
        return;
      }
      if (nextState !== 'active') return;
      void nativeModules.logStreamLifecycle('APP_FOREGROUND', `AppState=${nextState}`);
      flowLog('SCREEN_SHARE_FLOW', 'AppState active — JS streaming listener will resume', {
        state: nextState,
      });
      restoreDeviceCredentials().then(async token => {
        if (!token) {
          navTraceAuth('AppState active — no token', {tokenExists: false});
          return;
        }
        const route = getCurrentChildRoute();
        const autoLockSync = isAutoLockSuppressed();
        const autoLockAsync = await isAutoLockSuppressedAsync();
        navTraceObserver('RootNavigator', 'AppState.active restoreDeviceCredentials', route, {
          route,
          autoLockSync,
          autoLockAsync,
          tokenExists: true,
        }, false);
        if (!route || isSetupOrPairingRoute(route)) {
          flowLog('SCREEN_SHARE_FLOW', 'AppState active — skip ServiceStatus reset during setup', {
            route: route ?? 'unknown',
          });
          return;
        }
        if (route === 'CheckPermissions' || route === 'DebugDashboard') {
          flowLog('SCREEN_SHARE_FLOW', 'AppState active — skip re-lock on permissions/diagnostics', {
            route,
          });
          navTrace('AppState active — skip re-lock', {screen: route, reason: 'on_diagnostics_or_permissions'});
          startStreamingListener();
          void nativeModules.setJsStreamingListenerActive(true);
          return;
        }
        if (isAutoLockSuppressed() || autoLockAsync || isStreamActive() || isStreamConnecting()) {
          // A screen-capture consent dialog or a live stream is in flight — the app only
          // briefly lost the foreground because of that system UI, not a real backgrounding.
          // Re-locking here would tear down the in-progress stream/consent flow and bounce
          // the child back to the parent-password screen for no reason.
          flowLog('SCREEN_SHARE_FLOW', 'AppState active — skip ServiceStatus reset, stream/consent in flight', {
            route,
          });
          // Re-attach the JS stream-wake listener that the background handler detached —
          // otherwise the app looks foregrounded but silently stops responding to
          // subsequent stream requests until the next real re-lock cycle.
          startStreamingListener();
          void nativeModules.setJsStreamingListenerActive(true);
          return;
        }
        const name = await getParentDisplayName();
        if (name) {
          setPairedParentDisplayName(name);
        }
        navTraceTransition(route ?? '?', 'ChildLogin', 'AppState active re-lock', 'RootNavigator.AppState', {
          returnTo: 'ServiceStatus',
          loginRedirectRequested: true,
        });
        resetChildNavigation('ChildLogin', {returnTo: 'ServiceStatus'});
        const hmacKey = await getDeviceHmacKey();
        await startMonitoringSync(token, hmacKey ?? undefined, name ?? undefined);
        // Re-attach the JS stream listener so parent stream requests are handled
        // immediately — even while the lock screen is showing. The app IS in the
        // foreground so the JS runtime is live and can receive wake events.
        startStreamingListener();
        void nativeModules.setJsStreamingListenerActive(true);
      });
    };
    const subscription = AppState.addEventListener('change', onAppStateChange);
    return () => subscription.remove();
  }, []);

  const markPaired = (parentDisplayName?: string) => {
    navTraceTransition(getNavStackSnapshot().active, 'ServiceStatus', 'markPaired', 'RootNavigator.markPaired', {
      parentDisplayName: parentDisplayName ?? null,
    });
    if (parentDisplayName?.trim()) {
      setPairedParentDisplayName(parentDisplayName.trim());
    }
    resetChildNavigation('ServiceStatus');
  };

  const markUnpaired = async () => {
    navTraceTransition(getNavStackSnapshot().active, 'ChildLogin', 'markUnpaired', 'RootNavigator.markUnpaired');
    setPairedParentDisplayName(null);
    stopMonitoringSync();
    await clearDeviceCredentials();
    await clearOnboardingSession();
    resetChildNavigation('ChildLogin');
  };

  const markRePairing = async () => {
    navTraceTransition(getNavStackSnapshot().active, 'Pairing', 'markRePairing', 'RootNavigator.markRePairing');
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
        screenListeners={{
          focus: e => {
            navTraceLifecycle(e.target?.split('-')[0] ?? String(e.target), 'onResume', {
              navigationEvent: 'focus',
              target: e.target,
              type: e.type,
            });
          },
          blur: e => {
            navTraceLifecycle(e.target?.split('-')[0] ?? String(e.target), 'onPause', {
              navigationEvent: 'blur',
              target: e.target,
              type: e.type,
            });
          },
        }}
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
