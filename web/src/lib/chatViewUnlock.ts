let unlocked = false;
const listeners = new Set<() => void>();

export function setChatViewUnlocked(value: boolean) {
  if (unlocked === value) {
    return;
  }
  unlocked = value;
  listeners.forEach(listener => listener());
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('pcp-chat-view-lock', {detail: value}));
  }
}

export function isChatViewUnlocked(): boolean {
  return unlocked;
}

export function subscribeChatViewUnlocked(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
