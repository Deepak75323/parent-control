import type {ChatMessageItem} from '../types/api';
import {deletedNoticeStillVisible, deletedNoticeTombstone} from './deletedNotice';

let showDeleted = false;
const listeners = new Set<() => void>();

export function setShowDeletedMessagesForOwner(value: boolean) {
  if (showDeleted === value) {
    return;
  }
  showDeleted = value;
  listeners.forEach(listener => listener());
}

export function isShowDeletedMessagesForOwner(): boolean {
  return showDeleted;
}

export function subscribeShowDeletedMessagesForOwner(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Drop a removed message unless the delete notice is still on screen, or the owner asked to see it. */
export function messageForOwnerDeletedView(
  message: ChatMessageItem,
  options: {
    removedForEveryone: boolean;
    showDeleted: boolean;
    viewerIsRoomOwner: boolean;
    nowMs?: number;
  },
): ChatMessageItem | null {
  const deleted = Boolean(message.deleted) || options.removedForEveryone;
  if (!deleted) {
    return message;
  }
  if (options.showDeleted && options.viewerIsRoomOwner) {
    return message.deleted ? message : {...message, deleted: true};
  }
  const nowMs = options.nowMs ?? Date.now();
  if (deletedNoticeStillVisible(message.deletedVisibleUntil, nowMs)) {
    return deletedNoticeTombstone(message);
  }
  if (!message.ownMessage && message.unread) {
    return deletedNoticeTombstone(message);
  }
  return null;
}
