const AVATAR_COLORS = [
  '#5b5ceb',
  '#632ce5',
  '#4140d1',
  '#7c4dff',
  '#2f2bc1',
  '#6366f1',
  '#8b5cf6',
  '#a855f7',
  '#4f46e5',
  '#7c3aed',
] as const;

function hashString(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }
  return hash;
}

/** Two-letter initials from a display name (e.g. "Golden Lily" → "GL"). */
export function getAvatarInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0]!.charAt(0)}${parts[parts.length - 1]!.charAt(0)}`.toUpperCase();
  }
  const single = parts[0] ?? '?';
  return single.slice(0, 2).toUpperCase();
}

/** Stable accent color for a user/room id or name — same seed always yields same color. */
export function getAvatarColor(seed: string): string {
  const normalized = seed.trim().toLowerCase();
  if (!normalized) {
    return AVATAR_COLORS[0];
  }
  return AVATAR_COLORS[hashString(normalized) % AVATAR_COLORS.length] ?? AVATAR_COLORS[0];
}
