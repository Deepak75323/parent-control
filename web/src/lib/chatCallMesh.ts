/** Normalize auth/call user ids so UUID case never creates a ghost "self" peer. */
export function normalizeCallUserId(userId: string | null | undefined): string {
  return (userId ?? '').trim().toLowerCase();
}

export function sameCallUserId(
  a: string | null | undefined,
  b: string | null | undefined,
): boolean {
  const left = normalizeCallUserId(a);
  const right = normalizeCallUserId(b);
  return Boolean(left) && left === right;
}

/** Perfect-negotiation helper: lower UUID is polite (yields on glare). */
export function isPolitePeer(localUserId: string, remoteUserId: string): boolean {
  return normalizeCallUserId(localUserId).localeCompare(normalizeCallUserId(remoteUserId)) < 0;
}

export function shouldCreateOffer(localUserId: string, remoteUserId: string): boolean {
  // Impolite peer (higher id) creates the initial offer.
  return !isPolitePeer(localUserId, remoteUserId);
}

/** Active remotes only — never treat local user as a peer tile. */
export function remoteCallParticipantIds(
  participants: ReadonlyArray<{userId: string}>,
  myUserId: string,
): string[] {
  const me = normalizeCallUserId(myUserId);
  if (!me) {
    return [];
  }
  const seen = new Set<string>();
  const remotes: string[] = [];
  for (const participant of participants) {
    const id = normalizeCallUserId(participant.userId);
    if (!id || id === me || seen.has(id)) {
      continue;
    }
    seen.add(id);
    remotes.push(id);
  }
  return remotes;
}

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** True when a string is a bare user/call id — never show these in the UI. */
export function looksLikeUserId(value: string | null | undefined): boolean {
  const text = (value ?? '').trim();
  return Boolean(text) && UUID_RE.test(text);
}

/**
 * Human label for a call peer. Prefer nickname map; never fall back to raw UUID
 * (signaling often arrives before participant nicknames are in the call payload).
 */
export function resolveCallPeerLabel(
  userId: string | null | undefined,
  labelsByUserId: ReadonlyMap<string, string>,
): string {
  const id = normalizeCallUserId(userId);
  if (!id) {
    return 'Parent';
  }
  const mapped = labelsByUserId.get(id)?.trim();
  if (mapped && !looksLikeUserId(mapped)) {
    return mapped;
  }
  return 'Parent';
}
