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

/** Human-readable age since last server check-in (for stale-readiness messaging). */
export function formatLastHeardAgo(
  lastSeenAt?: string | null,
  nowMs: number = Date.now(),
): string | null {
  if (!lastSeenAt) return null;
  const lastSeenMs = Date.parse(lastSeenAt);
  if (Number.isNaN(lastSeenMs)) return null;

  const ageMs = Math.max(0, nowMs - lastSeenMs);
  const minutes = Math.floor(ageMs / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hr${hours === 1 ? '' : 's'} ago`;

  const days = Math.floor(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

/** True when device is offline but the checklist still shows granted items (stale snapshot). */
export function hasStaleGrantedReadiness(
  online: boolean,
  readinessItems: ReadonlyArray<{key: string; ok: boolean | null}>,
): boolean {
  if (online) return false;
  return readinessItems.some(item => item.key !== 'online' && item.ok === true);
}

/** True when permission rows look granted but the child snapshot is too old to trust. */
export function hasAgedReadinessSnapshot(
  online: boolean,
  lastSeenAt: string | null | undefined,
  readinessItems: ReadonlyArray<{key: string; ok: boolean | null}>,
  nowMs: number = Date.now(),
  /** Heartbeats are ~5 min; warn once the last check-in is older than one cycle. */
  maxAgeMs: number = 6 * 60 * 1000,
): boolean {
  if (!online) return false;
  if (!lastSeenAt) return false;
  const lastSeenMs = Date.parse(lastSeenAt);
  if (Number.isNaN(lastSeenMs) || nowMs - lastSeenMs <= maxAgeMs) return false;
  return readinessItems.some(item => item.key !== 'online' && item.ok === true);
}

export function buildStaleReadinessMessage(
  lastSeenAt?: string | null,
  nowMs: number = Date.now(),
): string {
  const ago = formatLastHeardAgo(lastSeenAt, nowMs);
  if (ago) {
    return `Permission checks below may be outdated (last heard ${ago}). Open Clock Time on the child phone to refresh.`;
  }
  return 'Permission checks below may be outdated. Open Clock Time on the child phone to refresh.';
}
