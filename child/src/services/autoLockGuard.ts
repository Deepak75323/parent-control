/**
 * Suppresses the child foreground re-lock (RootNavigator resetting to ChildLogin)
 * while a screen-capture consent dialog or a live stream is in flight.
 *
 * The system MediaProjection dialog briefly backgrounds/foregrounds the app. Without this
 * guard, RootNavigator's AppState 'active' handler treats that as a normal re-foreground
 * and force-resets the navigation stack to the parent-password lock screen — even though
 * the child never actually left the app. See RootNavigator.tsx for the consumer.
 *
 * The skip window is persisted in native SharedPreferences so it survives a React remount
 * (common on MIUI when MediaProjectionConsentActivity finishes and MainActivity resumes).
 */

import {nativeModules} from '../nativeModules';
import {navTrace, navTraceObserver} from '../navigation/navTrace';

let suppressDepth = 0;
let justAuthenticatedUntilMs = 0;
let cooldownUntilMs = 0;

/** Active grant / consent — keep hot through the whole flow. */
const GRANT_WINDOW_MS = 45_000;

/**
 * Grace after the flow ends. Native consent resolves its JS promise before
 * MainActivity resumes, and MIUI can deliver AppState 'active' seconds later.
 */
const RELEASE_COOLDOWN_MS = 15_000;

function persistSkipUntil(untilMs: number): void {
  cooldownUntilMs = Math.max(cooldownUntilMs, untilMs);
  void nativeModules.setSkipAutoRelockUntil(untilMs).catch(() => undefined);
}

/** Begin suppressing auto re-lock. Pair with `endSuppressAutoLock()` in a finally block. */
export function beginSuppressAutoLock(): void {
  suppressDepth += 1;
  persistSkipUntil(Date.now() + GRANT_WINDOW_MS);
}

export function endSuppressAutoLock(): void {
  suppressDepth = Math.max(0, suppressDepth - 1);
  if (suppressDepth === 0) {
    persistSkipUntil(Date.now() + RELEASE_COOLDOWN_MS);
  }
}

/** Mark that login just succeeded — re-lock is suppressed for a short grace window. */
export function markJustAuthenticated(graceMs = 10_000): void {
  justAuthenticatedUntilMs = Date.now() + graceMs;
  persistSkipUntil(justAuthenticatedUntilMs);
  navTrace('markJustAuthenticated', {
    graceMs,
    suppressDepth,
    untilMs: justAuthenticatedUntilMs,
  });
}

export function isAutoLockSuppressed(): boolean {
  const now = Date.now();
  const suppressed = suppressDepth > 0 || now < justAuthenticatedUntilMs || now < cooldownUntilMs;
  navTraceObserver('autoLockGuard', 'isAutoLockSuppressed', null, suppressed, false, {
    suppressDepth,
    justAuthenticatedUntilMs,
    cooldownUntilMs,
    now,
  });
  return suppressed;
}

/** In-memory + native persisted skip (survives React remount / process warm-start). */
export async function isAutoLockSuppressedAsync(): Promise<boolean> {
  if (isAutoLockSuppressed()) {
    return true;
  }
  try {
    return await nativeModules.isSkipAutoRelockActive();
  } catch {
    return false;
  }
}
