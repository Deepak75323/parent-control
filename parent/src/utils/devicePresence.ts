/**
 * Parent-side presence display.
 *
 * Child heartbeats ~every 5 minutes; backend flips to OFFLINE after ~8 minutes.
 * Trust an explicit ONLINE status, and treat a fresh lastSeen as present even when
 * status briefly lags as OFFLINE (streaming can still work in that window).
 */
/** Match backend DevicePresenceScheduler.OFFLINE_THRESHOLD_MINUTES. */
export const PRESENCE_STALE_MS = 8 * 60 * 1000;

export function isDevicePresentOnline(
  status?: string | null,
  lastSeenAt?: string | null,
  nowMs: number = Date.now(),
): boolean {
  const normalized = (status ?? '').toUpperCase();
  if (normalized === 'REVOKED') {
    return false;
  }

  const lastSeenMs = lastSeenAt ? Date.parse(lastSeenAt) : Number.NaN;
  const hasLastSeen = !Number.isNaN(lastSeenMs);
  const fresh = hasLastSeen && nowMs - lastSeenMs <= PRESENCE_STALE_MS;

  if (normalized === 'ONLINE') {
    // Demote only when lastSeen is past the server offline window (stale ONLINE row).
    if (hasLastSeen && !fresh) {
      return false;
    }
    return true;
  }

  // OFFLINE / unknown: a fresh check-in means the device is still reachable
  // (status can lag while streams and commands still succeed).
  if (fresh) {
    return true;
  }

  return false;
}
