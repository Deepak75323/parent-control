import React from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {RootNavigator} from './src/navigation/RootNavigator';
import {navigationRef, flushPendingDeviceListNavigation} from './src/navigation/navigationRef';
import {WebSocketLifecycle} from './src/components/WebSocketLifecycle';
import {PairingNavigationListener} from './src/components/PairingNavigationListener';
import {PairingPollListener} from './src/components/PairingPollListener';

export default function App() {
  return (
    <SafeAreaProvider>
      <WebSocketLifecycle />
      <PairingPollListener />
      <NavigationContainer ref={navigationRef} onReady={flushPendingDeviceListNavigation}>
        <PairingNavigationListener />
        <RootNavigator />
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
