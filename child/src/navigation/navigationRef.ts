import {createNavigationContainerRef, CommonActions} from '@react-navigation/native';
import type {ChildStackParamList} from '../screens/RootNavigator';
import {
  getNavStackSnapshot,
  navTraceNavCall,
  navTracePostNav,
  navTraceTransition,
} from './navTrace';
import {
  blockDebugDashboardReopen,
  suppressServiceStatusMenus,
} from './navigationInputGuard';

export const navigationRef = createNavigationContainerRef<ChildStackParamList>();

type ChildRoute = keyof ChildStackParamList;

let pendingRoute: ChildRoute | null = null;

export function resetChildNavigation(
  route: ChildRoute,
  params?: ChildStackParamList[ChildRoute],
): void {
  const caller = 'navigationRef.resetChildNavigation';
  const snap = getNavStackSnapshot();
  navTraceNavCall('reset', caller, route, 'before', {
    params: params ?? null,
    stackBefore: snap.stack,
    activeBefore: snap.active,
  });
  if (!navigationRef.isReady()) {
    navTraceTransition(snap.active, route, 'navigationRef not ready — queued pending route', caller, {
      params: params ?? null,
    });
    pendingRoute = route;
    pendingParams = params;
    return;
  }
  pendingRoute = null;
  pendingParams = undefined;
  navTraceTransition(snap.active, route, 'CommonActions.reset', caller, {params: params ?? null});
  navigationRef.dispatch(
    CommonActions.reset({
      index: 0,
      routes: [{name: route, params}],
    }),
  );
  navTraceNavCall('reset', caller, route, 'after', {
    stackAfter: getNavStackSnapshot().stack,
  });
  navTracePostNav(caller);
}

let pendingParams: ChildStackParamList[ChildRoute] | undefined;

export function flushPendingChildNavigation(): void {
  if (pendingRoute) {
    navTraceTransition('PENDING', pendingRoute, 'flushPendingChildNavigation', 'navigationRef.flushPendingChildNavigation', {
      params: pendingParams ?? null,
    });
    resetChildNavigation(pendingRoute, pendingParams);
  }
}

export function navigateToStreamIncoming(): void {
  const caller = 'navigationRef.navigateToStreamIncoming';
  const snap = getNavStackSnapshot();
  navTraceNavCall('navigate', caller, 'StreamIncoming', 'before', {stackBefore: snap.stack});
  if (!navigationRef.isReady()) {
    navTraceTransition(snap.active, 'StreamIncoming', 'navigationRef not ready — queued', caller);
    pendingRoute = 'StreamIncoming';
    return;
  }
  navigationRef.navigate('StreamIncoming');
  navTraceNavCall('navigate', caller, 'StreamIncoming', 'after', {
    stackAfter: getNavStackSnapshot().stack,
  });
  navTracePostNav(caller);
}

export function getCurrentChildRoute(): ChildRoute | undefined {
  if (!navigationRef.isReady()) {
    return undefined;
  }
  return navigationRef.getCurrentRoute()?.name as ChildRoute | undefined;
}

/**
 * Deterministically return to the Monitoring Active (ServiceStatus) home from a
 * settings sub-screen (Diagnostics / Grant Permissions).
 *
 * Bare `navigation.goBack()` depends on implicit stack history: depending on
 * whether the user reached Diagnostics directly or via
 * ServiceStatus → Diagnostics → Grant Permissions, "back" could step onto
 * another settings sub-screen instead of Monitoring Active — the behaviour
 * behind the reported Monitoring-Active ⇄ Diagnostics bounce. Popping to the
 * existing ServiceStatus entry (or resetting to it if, defensively, it is not on
 * the stack) guarantees Monitoring Active becomes visible and stays visible, with
 * no duplicate entries left behind and no possibility of a loop.
 */
export function goToMonitoringHome(): void {
  const caller = 'navigationRef.goToMonitoringHome';
  blockDebugDashboardReopen();
  const snap = getNavStackSnapshot();
  navTraceNavCall('reset', caller, 'ServiceStatus', 'before', {stackBefore: snap.stack});
  if (!navigationRef.isReady()) {
    navTraceTransition(snap.active, 'ServiceStatus', 'navigationRef not ready', caller);
    resetChildNavigation('ServiceStatus');
    return;
  }
  const state = navigationRef.getState();
  const routes = state?.routes ?? [];
  const serviceStatusIdx = routes.findIndex(r => r.name === 'ServiceStatus');

  if (serviceStatusIdx < 0) {
    navTraceTransition(snap.active, 'ServiceStatus', 'ServiceStatus not in stack — hard reset', caller);
    resetChildNavigation('ServiceStatus');
    return;
  }

  navTraceTransition(snap.active, 'ServiceStatus', 'hard reset to monitoring home', caller, {
    serviceStatusIdx,
    routeCount: routes.length,
  });
  // Hard-reset the stack to exactly [ServiceStatus].
  //
  // Both CommonActions.navigate and StackActions.pop dispatched via the root
  // navigationRef can leave ghost entries in the native-stack history when the
  // screen is mid-transition or when the ref points to a stale snapshot.  That
  // is what produced the Monitoring Active ⇄ Diagnostics bounce: a new
  // ServiceStatus was pushed on top of the existing DebugDashboard route, so
  // pressing hardware-back immediately fell back to DebugDashboard.
  //
  // CommonActions.reset (used by resetChildNavigation) unconditionally replaces
  // the entire route list — no ghost entries are possible.
  resetChildNavigation('ServiceStatus');
}

/** Routes where auto-resume to ServiceStatus would interrupt first-time setup or pairing. */
export function isSetupOrPairingRoute(route: ChildRoute | undefined): boolean {
  return (
    route === 'ChildLogin' ||
    route === 'ChildRecovery' ||
    route === 'PermissionWizard' ||
    route === 'PermissionDashboard' ||
    route === 'OemSetupWizard' ||
    route === 'DeviceOwnerSetup' ||
    route === 'Pairing'
  );
}
