import {createNavigationContainerRef, CommonActions} from '@react-navigation/native';
import type {ChildStackParamList} from '../screens/RootNavigator';

export const navigationRef = createNavigationContainerRef<ChildStackParamList>();

type ChildRoute = keyof ChildStackParamList;

let pendingRoute: ChildRoute | null = null;

export function resetChildNavigation(route: ChildRoute): void {
  if (!navigationRef.isReady()) {
    pendingRoute = route;
    return;
  }
  pendingRoute = null;
  navigationRef.dispatch(
    CommonActions.reset({
      index: 0,
      routes: [{name: route}],
    }),
  );
}

export function flushPendingChildNavigation(): void {
  if (pendingRoute) {
    resetChildNavigation(pendingRoute);
  }
}

export function navigateToStreamIncoming(): void {
  if (!navigationRef.isReady()) {
    pendingRoute = 'StreamIncoming';
    return;
  }
  navigationRef.navigate('StreamIncoming');
}

export function getCurrentChildRoute(): ChildRoute | undefined {
  if (!navigationRef.isReady()) {
    return undefined;
  }
  return navigationRef.getCurrentRoute()?.name as ChildRoute | undefined;
}

/** Routes where auto-resume to ServiceStatus would interrupt first-time setup or pairing. */
export function isSetupOrPairingRoute(route: ChildRoute | undefined): boolean {
  return (
    route === 'SetupWizard' ||
    route === 'PermissionSetup' ||
    route === 'OemSetupWizard' ||
    route === 'DeviceOwnerSetup' ||
    route === 'Pairing'
  );
}
