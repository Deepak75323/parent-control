import React, {useRef} from 'react';
import {NavigationContainer, NavigationState, PartialState} from '@react-navigation/native';
import {GestureHandlerRootView} from 'react-native-gesture-handler';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {RootNavigator} from './src/screens/RootNavigator';
import {navigationRef, flushPendingChildNavigation} from './src/navigation/navigationRef';
import {navTrace, navTraceLifecycle, navTraceStateChange} from './src/navigation/navTrace';
import {initClientAnalytics} from './src/services/analyticsUtils';

initClientAnalytics();

type NavState = NavigationState | PartialState<NavigationState> | undefined;

function countNavDepth(state: NavState): number {
  if (!state?.routes?.length) {
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
  const prevNavState = useRef<NavigationState | undefined>(undefined);
  const navDepthRef = useRef(0);

  return (
    <GestureHandlerRootView style={{flex: 1}}>
      <SafeAreaProvider>
        <NavigationContainer
          ref={navigationRef}
          onReady={() => {
            navTraceLifecycle('App', 'onCreate', {phase: 'NavigationContainer.onReady'});
            navTrace('App Launch — NavigationContainer ready', {reason: 'app_launch'});
            flushPendingChildNavigation();
            navDepthRef.current = countNavDepth(navigationRef.getRootState());
          }}
          onStateChange={state => {
            const prevDepth = navDepthRef.current;
            navTraceStateChange(prevNavState.current, state, prevDepth);
            navDepthRef.current = countNavDepth(state);
            prevNavState.current = state;
          }}>
          <RootNavigator />
        </NavigationContainer>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
