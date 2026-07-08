/**
 * Suppresses the child-app foreground re-lock (RootNavigator resetting to ChildLogin)
 * while a screen-capture consent dialog or a live stream is in flight.
 *
 * The system MediaProjection dialog briefly backgrounds/foregrounds the app. Without this
 * guard, RootNavigator's AppState 'active' handler treats that as a normal re-foreground
 * and force-resets the navigation stack to the parent-password lock screen — even though
 * the child never actually left the app. See RootNavigator.tsx for the consumer.
 */

let suppressDepth = 0;
let justAuthenticatedUntilMs = 0;

/** Begin suppressing auto re-lock. Pair with `endSuppressAutoLock()` in a finally block. */
export function beginSuppressAutoLock(): void {
  suppressDepth += 1;
}

export function endSuppressAutoLock(): void {
  suppressDepth = Math.max(0, suppressDepth - 1);
}

/** Mark that login just succeeded — re-lock is suppressed for a short grace window. */
export function markJustAuthenticated(graceMs = 10_000): void {
  justAuthenticatedUntilMs = Date.now() + graceMs;
}

export function isAutoLockSuppressed(): boolean {
  return suppressDepth > 0 || Date.now() < justAuthenticatedUntilMs;
}
