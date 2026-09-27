import type {ChatMessageItem} from '../types/api';

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

/** Drop a message removed for everyone, unless this viewer is the room owner and asked to see it. */
export function messageForOwnerDeletedView(
  message: ChatMessageItem,
  options: {removedForEveryone: boolean; showDeleted: boolean; viewerIsRoomOwner: boolean},
): ChatMessageItem | null {
  const deleted = Boolean(message.deleted) || options.removedForEveryone;
  if (!deleted) {
    return message;
  }
  if (options.showDeleted && options.viewerIsRoomOwner) {
    return message.deleted ? message : {...message, deleted: true};
  }
  return null;
}
