import React, {useRef} from 'react';
import {NavigationContainer} from '@react-navigation/native';
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

initTelemetry();

export default function App() {
  const routeNameRef = useRef<string | undefined>();

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
          if (current) {
            trackScreen(current);
          }
        }}
        onStateChange={state => {
          const next = getActiveRouteName(state);
          if (next && next !== routeNameRef.current) {
            routeNameRef.current = next;
            trackScreen(next);
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
