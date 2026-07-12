import React, {useRef} from 'react';
import {NavigationContainer, NavigationState} from '@react-navigation/native';
import {GestureHandlerRootView} from 'react-native-gesture-handler';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {RootNavigator} from './src/screens/RootNavigator';
import {navigationRef, flushPendingChildNavigation} from './src/navigation/navigationRef';
import {navTrace, navTraceLifecycle, navTraceStateChange} from './src/navigation/navTrace';

export default function App() {
  const prevNavState = useRef<NavigationState | undefined>(undefined);

  return (
    <GestureHandlerRootView style={{flex: 1}}>
      <SafeAreaProvider>
        <NavigationContainer
          ref={navigationRef}
          onReady={() => {
            navTraceLifecycle('App', 'onCreate', {phase: 'NavigationContainer.onReady'});
            navTrace('App Launch — NavigationContainer ready', {reason: 'app_launch'});
            flushPendingChildNavigation();
          }}
          onStateChange={state => {
            navTraceStateChange(prevNavState.current, state);
            prevNavState.current = state;
          }}>
          <RootNavigator />
        </NavigationContainer>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
