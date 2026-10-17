/** Format partner Online / Last seen for 2-person chats. */

export type PartnerPresenceView = {
  online: boolean;
  lastSeenAt?: string | null;
  hidden?: boolean;
};

export function formatLastSeenAgo(
  lastSeenAt?: string | null,
  nowMs: number = Date.now(),
): string | null {
  if (!lastSeenAt) {
    return null;
  }
  const lastSeenMs = Date.parse(lastSeenAt);
  if (Number.isNaN(lastSeenMs)) {
    return null;
  }
  const ageMs = Math.max(0, nowMs - lastSeenMs);
  const minutes = Math.floor(ageMs / 60_000);
  if (minutes < 1) {
    return 'just now';
  }
  if (minutes < 60) {
    return `${minutes} min ago`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} hr${hours === 1 ? '' : 's'} ago`;
  }
  const days = Math.floor(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

/** Subtitle fragment for DM header; null when hidden or unknown. */
export function formatPartnerPresenceLabel(
  presence: PartnerPresenceView | null | undefined,
  nowMs: number = Date.now(),
): string | null {
  if (!presence || presence.hidden) {
    return null;
  }
  if (presence.online) {
    return 'Online';
  }
  const ago = formatLastSeenAgo(presence.lastSeenAt, nowMs);
  if (ago) {
    return `Last seen ${ago}`;
  }
  return 'Offline';
}

/** Absolute clock chip for offline members, e.g. "Today, 10:45 PM". */
export function formatLastSeenClockLabel(
  lastSeenAt?: string | null,
  nowMs: number = Date.now(),
): string | null {
  if (!lastSeenAt) {
    return null;
  }
  const lastSeenMs = Date.parse(lastSeenAt);
  if (Number.isNaN(lastSeenMs)) {
    return null;
  }
  const seen = new Date(lastSeenMs);
  const now = new Date(nowMs);
  const time = seen.toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  });
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfSeen = new Date(seen.getFullYear(), seen.getMonth(), seen.getDate()).getTime();
  const dayDiff = Math.round((startOfToday - startOfSeen) / 86_400_000);
  if (dayDiff === 0) {
    return `Today, ${time}`;
  }
  if (dayDiff === 1) {
    return `Yesterday, ${time}`;
  }
  const date = seen.toLocaleDateString(undefined, {month: 'short', day: 'numeric'});
  return `${date}, ${time}`;
}
