import type {ChatMessageItem} from '../types/api';

/** Same wait as a normal note after it is read (app.chat.auto-delete.grace-after-read). */
export const DELETED_NOTICE_GRACE_MS = 15 * 60 * 1000;

export function deletedNoticeVisibleUntil(fromMs = Date.now()): string {
  return new Date(fromMs + DELETED_NOTICE_GRACE_MS).toISOString();
}

export function deletedNoticeStillVisible(
  until: string | null | undefined,
  nowMs: number,
): boolean {
  if (!until) {
    return false;
  }
  const expiresAt = Date.parse(until);
  return Number.isFinite(expiresAt) && expiresAt > nowMs;
}

/** After the deleted notice is read, keep it for the same grace window as a normal note. */
export function withDeletedNoticeRead(message: ChatMessageItem, nowMs = Date.now()): ChatMessageItem {
  if (message.ownMessage) {
    return message;
  }
  if (!message.deleted) {
    return {...message, unread: false};
  }
  return {
    ...message,
    unread: false,
    deletedVisibleUntil: message.deletedVisibleUntil ?? deletedNoticeVisibleUntil(nowMs),
  };
}

export function deletedNoticeTombstone(
  message: ChatMessageItem,
  until?: string,
): ChatMessageItem {
  return {
    ...message,
    deleted: true,
    body: '',
    attachmentUrl: undefined,
    deletedVisibleUntil: until ?? message.deletedVisibleUntil,
  };
}
