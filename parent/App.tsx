import React, {useRef} from 'react';
import {NavigationContainer} from '@react-navigation/native';
import type {NavigationState, PartialState} from '@react-navigation/native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {RootNavigator} from './src/navigation/RootNavigator';
import {linking} from './src/navigation/linking';
import {navigationRef, flushPendingDeviceListNavigation} from './src/navigation/navigationRef';
import {getActiveRouteName} from './src/navigation/getActiveRouteName';
import {WebSocketLifecycle} from './src/components/WebSocketLifecycle';
import {SessionKeepAlive} from './src/components/SessionKeepAlive';
import {PairingNavigationListener} from './src/components/PairingNavigationListener';
import {PairingPollListener} from './src/components/PairingPollListener';
import {AppDialogHost} from './src/components/AppDialogHost';
import {ClockLockHost} from './src/components/ClockLockHost';
import {initTelemetry, trackScreen} from './src/services/telemetry';
import {
  initClientAnalytics,
  trackBackAnalytics,
  trackScreenAnalytics,
} from './src/services/analyticsUtils';

initTelemetry();
initClientAnalytics();

type NavState = NavigationState | PartialState<NavigationState> | undefined;

function countNavDepth(state: NavState): number {
  if (!state || !('routes' in state) || !state.routes?.length) {
    return 0;
  }
  const index = state.index ?? state.routes.length - 1;
  const route = state.routes[index];
  if (route.state) {
    return 1 + countNavDepth(route.state as NavState);
  }
  return 1;
}

export default function App() {
  const routeNameRef = useRef<string | undefined>();
  const navDepthRef = useRef(0);

  return (
    <SafeAreaProvider>
      <WebSocketLifecycle />
      <SessionKeepAlive />
      <PairingPollListener />
      <NavigationContainer
        ref={navigationRef}
        linking={linking}
        onReady={() => {
          flushPendingDeviceListNavigation();
          const current = navigationRef.getCurrentRoute()?.name;
          routeNameRef.current = current;
          navDepthRef.current = countNavDepth(navigationRef.getRootState());
          if (current) {
            trackScreen(current);
            trackScreenAnalytics(current);
          }
        }}
        onStateChange={state => {
          const next = getActiveRouteName(state);
          const prev = routeNameRef.current;
          const prevDepth = navDepthRef.current;
          const nextDepth = countNavDepth(state);
          navDepthRef.current = nextDepth;
          if (next && next !== prev) {
            if (prev && nextDepth < prevDepth) {
              trackBackAnalytics(prev, next);
            }
            routeNameRef.current = next;
            trackScreen(next);
            trackScreenAnalytics(next);
          }
        }}>
        <PairingNavigationListener />
        <RootNavigator />
      </NavigationContainer>
      <ClockLockHost />
      <AppDialogHost />
    </SafeAreaProvider>
  );
}
