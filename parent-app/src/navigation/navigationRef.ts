import {createNavigationContainerRef, CommonActions} from '@react-navigation/native';
import type {RootStackParamList} from './RootNavigator';

export const navigationRef = createNavigationContainerRef<RootStackParamList>();

let pendingDeviceListNavigation = false;

/** Navigate parent to DeviceList when child pairing completes (WS event). */
export function navigateToDeviceList(): void {
  if (!navigationRef.isReady()) {
    pendingDeviceListNavigation = true;
    return;
  }
  pendingDeviceListNavigation = false;
  const route = navigationRef.getCurrentRoute();
  if (route?.name === 'DeviceList') {
    return;
  }
  navigationRef.dispatch(
    CommonActions.navigate({
      name: 'DeviceList',
      merge: true,
    }),
  );
}

/** Call from NavigationContainer onReady — flushes navigation queued before mount. */
export function flushPendingDeviceListNavigation(): void {
  if (pendingDeviceListNavigation) {
    navigateToDeviceList();
  }
}
