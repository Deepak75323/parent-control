import type {ChatMessageItem} from '../types/api';

/** User id for the logged-in parent, inferred from API messages marked ownMessage. */
export function resolveOwnSenderUserId(messages: ChatMessageItem[]): string | null {
  for (const message of messages) {
    if (message.ownMessage) {
      return message.senderUserId;
    }
  }
  return null;
}

/** Correct ownMessage when a realtime payload was broadcast with the sender's perspective. */
export function withResolvedOwnership(
  message: ChatMessageItem,
  ownSenderUserId: string | null,
): ChatMessageItem {
  if (!ownSenderUserId) {
    return message;
  }
  const isOwn = message.senderUserId === ownSenderUserId;
  if (message.ownMessage === isOwn) {
    return message;
  }
  return {
    ...message,
    ownMessage: isOwn,
    receiptStatus: isOwn ? message.receiptStatus : undefined,
  };
}
