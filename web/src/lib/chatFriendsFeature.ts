/** Opt-in chat personal features. Both default off unless env is exactly "true". */

export function isChatFriendsEnabled(
  raw: string | undefined = import.meta.env.VITE_ENABLE_CHAT_FRIENDS as string | undefined,
): boolean {
  return raw?.trim().toLowerCase() === 'true';
}

/** Direct messages list / DM tab. Off by default — servers only. */
export function isChatDmsEnabled(
  raw: string | undefined = import.meta.env.VITE_ENABLE_CHAT_DMS as string | undefined,
): boolean {
  return raw?.trim().toLowerCase() === 'true';
}
