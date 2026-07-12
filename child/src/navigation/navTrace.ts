import {redactForLogs} from '../utils/logRedaction';
import {navigationRef} from './navigationRef';

const TAG = '[NAV_TRACE]';

export type NavLifecycle =
  | 'onCreate'
  | 'onStart'
  | 'onResume'
  | 'onPause'
  | 'onStop'
  | 'onDestroy'
  | 'onRestart'
  | 'onNewIntent';

type NavTraceFields = Record<string, unknown>;

function threadName(): string {
  return 'JS-Main';
}

function timestamp(): string {
  return new Date().toISOString();
}

export function getNavStackSnapshot(): {
  ready: boolean;
  active: string;
  routes: string[];
  index: number;
  stack: string;
} {
  if (!navigationRef.isReady()) {
    return {ready: false, active: '?', routes: [], index: -1, stack: 'NOT_READY'};
  }
  const state = navigationRef.getState();
  const routes = state?.routes?.map(r => r.name) ?? [];
  const index = state?.index ?? 0;
  const active = routes[index] ?? '?';
  return {
    ready: true,
    active,
    routes,
    index,
    stack: routes.length ? routes.join(' > ') : '(empty)',
  };
}

function formatFields(fields: NavTraceFields): string {
  const parts = Object.entries(fields)
    .filter(([, v]) => v !== undefined)
    .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(redactForLogs(v)) : String(v)}`);
  return parts.length ? ` ${parts.join(' ')}` : '';
}

/** Core trace line — dev-only navigation diagnostics. */
export function navTrace(event: string, fields: NavTraceFields = {}): void {
  if (!__DEV__) return;
  const snap = getNavStackSnapshot();
  const line =
    `${TAG} ts=${timestamp()} thread=${threadName()}` +
    ` screen=${String(fields.screen ?? snap.active)}` +
    ` event=${event}` +
    ` stack=[${snap.stack}] active=${snap.active}` +
    formatFields({...fields, navStack: snap.stack, navIndex: snap.index, navRoutes: snap.routes});
  console.warn(line);
}

export function navTraceLifecycle(
  screen: string,
  lifecycle: NavLifecycle,
  extra: NavTraceFields = {},
): void {
  navTrace(`${screen} -> ${lifecycle}()`, {screen, lifecycle, lifecycleCallback: lifecycle, ...extra});
}

export function navTraceTransition(
  from: string,
  to: string,
  reason: string,
  caller: string,
  extra: NavTraceFields = {},
): void {
  navTrace(`FROM=${from} TO=${to}`, {
    from,
    to,
    reason,
    caller,
    navigationSource: caller,
    ...extra,
  });
}

export function navTraceNavCall(
  method: string,
  caller: string,
  destination: string,
  phase: 'before' | 'after',
  extra: NavTraceFields = {},
): void {
  navTrace(`${phase.toUpperCase()} navigation.${method}`, {
    method,
    caller,
    destination,
    phase,
    navigationSource: caller,
    ...extra,
  });
}

export function navTraceObserver(
  screen: string,
  observer: string,
  prevState: unknown,
  currentState: unknown,
  navigationTriggered: boolean,
  extra: NavTraceFields = {},
): void {
  navTrace('Observer Triggered', {
    screen,
    observer,
    previousState: prevState,
    currentState,
    navigationTriggered,
    ...extra,
  });
}

export function navTraceAuth(
  context: string,
  fields: {
    authenticated?: boolean;
    tokenExists?: boolean;
    tokenValid?: boolean;
    loginRedirectRequested?: boolean;
    onboardingAuthenticated?: boolean;
  },
): void {
  navTrace(`Auth check: ${context}`, {context, ...fields});
}

export function navTracePermissions(
  context: string,
  perms: Record<string, boolean | null | undefined>,
  navigationTriggered = false,
  extra: NavTraceFields = {},
): void {
  navTrace(`Permission check: ${context}`, {
    context,
    navigationTriggered,
    accessibility: perms.accessibility,
    deviceAdmin: perms.deviceAdmin,
    overlay: perms.overlay,
    notification: perms.notification,
    screenCapture: perms.screenCapture,
    camera: perms.camera,
    microphone: perms.microphone,
    usageStats: perms.usageStats,
    ...extra,
  });
}

export function navTraceStateChange(
  prevState: NavigationState | undefined,
  nextState: NavigationState | undefined,
): void {
  const prevRoutes = prevState?.routes?.map(r => r.name) ?? [];
  const nextRoutes = nextState?.routes?.map(r => r.name) ?? [];
  const prevActive = prevRoutes[prevState?.index ?? 0] ?? '?';
  const nextActive = nextRoutes[nextState?.index ?? 0] ?? '?';
  navTrace('NavigationContainer onStateChange', {
    screen: nextActive,
    from: prevActive,
    to: nextActive,
    reason: 'state_change',
    previousStack: prevRoutes.join(' > ') || '(empty)',
    nextStack: nextRoutes.join(' > ') || '(empty)',
    navigationTriggered: prevActive !== nextActive,
  });
}

/** Schedule stack snapshots after a navigation to detect follow-up redirects. */
export function navTracePostNav(caller: string, delaysMs: number[] = [0, 50, 150, 500]): void {
  for (const ms of delaysMs) {
    setTimeout(() => {
      const snap = getNavStackSnapshot();
      navTrace(`Post-nav snapshot +${ms}ms`, {
        caller,
        screen: snap.active,
        active: snap.active,
        stack: snap.stack,
        followUpCheck: true,
      });
    }, ms);
  }
}
