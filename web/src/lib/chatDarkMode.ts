const STORAGE_KEY = 'pcp-chat-dark';

export function readChatDarkMode(stored: string | null): boolean {
  return stored === '1';
}

export function chatDarkStorageValue(enabled: boolean): string {
  return enabled ? '1' : '0';
}

function readStored(): boolean {
  try {
    if (typeof localStorage === 'undefined') {
      return false;
    }
    return readChatDarkMode(localStorage.getItem(STORAGE_KEY));
  } catch {
    return false;
  }
}

let dark = readStored();
const listeners = new Set<() => void>();

export function isChatDarkMode(): boolean {
  return dark;
}

export function setChatDarkMode(enabled: boolean) {
  if (dark === enabled) {
    return;
  }
  dark = enabled;
  try {
    localStorage.setItem(STORAGE_KEY, chatDarkStorageValue(enabled));
  } catch {
    // The choice still applies for this visit when storage is blocked.
  }
  listeners.forEach(listener => listener());
}

export function subscribeChatDarkMode(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
