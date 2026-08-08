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
    if (hasLastSeen && !fresh) {
      return false;
    }
    return true;
  }

  if (fresh) {
    return true;
  }

  return false;
}

export function deviceInitials(name: string): string {
  return name
    .split(/\s+/)
    .map(w => w[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase();
}
