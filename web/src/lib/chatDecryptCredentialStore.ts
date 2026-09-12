/** Passphrase for encrypt-while-view-locked. Cleared on logout. Not the view-unlock flag. */

const PASSPHRASE_KEY = 'pcp_chat_decrypt_passphrase';

/** Fallback when sessionStorage is unavailable (SSR / some test runners). */
let memoryPassphrase: string | null = null;

export function rememberChatDecryptPassphrase(passphrase: string): void {
  memoryPassphrase = passphrase;
  try {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem(PASSPHRASE_KEY, passphrase);
    }
  } catch {
    /* private mode / quota */
  }
}

export function readRememberedChatDecryptPassphrase(): string | null {
  try {
    if (typeof sessionStorage !== 'undefined') {
      const stored = sessionStorage.getItem(PASSPHRASE_KEY);
      if (stored) {
        memoryPassphrase = stored;
        return stored;
      }
    }
  } catch {
    /* ignore */
  }
  return memoryPassphrase;
}

export function clearRememberedChatDecryptPassphrase(): void {
  memoryPassphrase = null;
  try {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem(PASSPHRASE_KEY);
    }
  } catch {
    /* ignore */
  }
}

export function hasRememberedChatDecryptPassphrase(): boolean {
  return Boolean(readRememberedChatDecryptPassphrase());
}
