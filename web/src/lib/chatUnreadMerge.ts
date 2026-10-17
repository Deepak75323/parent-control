/**
 * WhatsApp-style unread: messages after the read cursor (lastReadMessageId).
 * UI unread count/divider are derived from the cursor — not a mutable counter.
 */

export type CursorMessage = {
  id: string;
  createdAt: string;
  ownMessage?: boolean;
  unread?: boolean;
};

/** True when {@code message} is after the read cursor in timeline order. */
export function isAfterReadCursor(
  message: CursorMessage,
  lastReadMessageId: string | null,
  messages: CursorMessage[],
): boolean {
  if (!lastReadMessageId) {
    return Boolean(message.unread);
  }
  const cursor = messages.find(entry => entry.id === lastReadMessageId);
  if (!cursor) {
    return Boolean(message.unread);
  }
  return message.createdAt > cursor.createdAt;
}

/** Incoming messages after lastReadMessageId (excludes own). */
export function unreadAfterCursor<T extends CursorMessage>(
  messages: T[],
  lastReadMessageId: string | null,
): T[] {
  return messages.filter(
    message =>
      !message.ownMessage && isAfterReadCursor(message, lastReadMessageId, messages),
  );
}

/** Message immediately before {@code messageId}, or null if none. */
export function previousMessageId<T extends CursorMessage>(
  messages: T[],
  messageId: string,
): string | null {
  const ordered = [...messages].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
  );
  const index = ordered.findIndex(message => message.id === messageId);
  if (index <= 0) {
    return null;
  }
  return ordered[index - 1].id;
}

/**
 * Prefer advancing the cursor only forward (by createdAt).
 * Returns the chosen cursor id.
 */
export function advanceReadCursor(
  currentLastReadId: string | null,
  candidateId: string,
  messages: CursorMessage[],
): string {
  if (!currentLastReadId) {
    return candidateId;
  }
  const current = messages.find(message => message.id === currentLastReadId);
  const candidate = messages.find(message => message.id === candidateId);
  if (!current || !candidate) {
    return candidateId;
  }
  return candidate.createdAt >= current.createdAt ? candidateId : currentLastReadId;
}

/** Overlay live WS unread without fighting a local cursor decision. */
export function mergeChatUnreadFlag(
  localUnread: boolean | undefined,
  incomingUnread: boolean | undefined,
): boolean | undefined {
  if (localUnread !== undefined) {
    return localUnread;
  }
  return incomingUnread;
}
