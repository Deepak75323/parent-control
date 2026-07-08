import {createNavigationContainerRef, CommonActions} from '@react-navigation/native';
import type {RootStackParamList} from './RootNavigator';
import {resolvePairingDestination} from './pairingNavigation';

export const navigationRef = createNavigationContainerRef<RootStackParamList>();

let pendingDeviceListNavigation = false;

/** Navigate parent to Pair & manage (gates on family child login password). */
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
  void resolvePairingDestination().then(dest => {
    if (!navigationRef.isReady()) {
      return;
    }
    navigationRef.dispatch(
      CommonActions.navigate({
        name: dest.name,
        params: dest.params,
        merge: true,
      }),
    );
  });
}

/** Call from NavigationContainer onReady — flushes navigation queued before mount. */
export function flushPendingDeviceListNavigation(): void {
  if (pendingDeviceListNavigation) {
    navigateToDeviceList();
  }
}
