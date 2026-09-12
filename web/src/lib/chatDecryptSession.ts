import {messageContainsDecryptCode} from './chatDecryptCode';
import {
  clearRememberedChatDecryptPassphrase,
  readRememberedChatDecryptPassphrase,
  rememberChatDecryptPassphrase,
} from './chatDecryptCredentialStore';
import {deriveChatCryptoKey, isEncryptedChatBody} from './chatMessageCrypto';

/** @deprecated Prefer lockedChatBodyDisplay(body) — kept for older call sites. */
export const LOCKED_CHAT_BODY = '🔒 Encrypted message';

const AES_PREFIX = 'pcp1:';

/**
 * Locked-room preview: looks like random text (not plaintext, not a lock label).
 * Stable for the same stored body so the bubble does not flicker.
 */
export function lockedChatBodyDisplay(body: string | null | undefined): string {
  if (!body) {
    return '';
  }
  if (isEncryptedChatBody(body)) {
    const raw = body
      .slice(AES_PREFIX.length)
      .replace(/[^a-zA-Z0-9]/g, '')
      .toLowerCase();
    if (!raw) {
      return '••••••••';
    }
    // Chunk into fake "words" so it reads like scrambled chat, not a crypto dump.
    return raw.match(/.{1,7}/g)?.join(' ') ?? raw;
  }
  // Legacy padded bodies already look like random letter soup.
  return body;
}

let sessionKey: CryptoKey | null = null;
let sessionSalt: string | null = null;
let sessionPassphrase: string | null = null;

/** Clears in-memory crypto only. Does not forget the remembered passphrase. */
export function clearChatDecryptSession(): void {
  sessionKey = null;
  sessionSalt = null;
  sessionPassphrase = null;
}

/** Logout / wipe: drop memory and remembered passphrase. */
export function wipeChatDecryptCredentials(): void {
  clearChatDecryptSession();
  clearRememberedChatDecryptPassphrase();
}

export function setChatDecryptSession(key: CryptoKey, salt: string, passphrase?: string): void {
  sessionKey = key;
  sessionSalt = salt;
  if (passphrase) {
    sessionPassphrase = passphrase;
    rememberChatDecryptPassphrase(passphrase);
  }
}

export function outgoingBodyLeaksDecryptCode(plaintext: string): boolean {
  const code = sessionPassphrase ?? readRememberedChatDecryptPassphrase();
  if (!code) {
    return false;
  }
  return messageContainsDecryptCode(plaintext, code);
}

export function getChatDecryptKey(): CryptoKey | null {
  return sessionKey;
}

export function getChatDecryptSalt(): string | null {
  return sessionSalt;
}

export function hasChatDecryptSession(): boolean {
  return sessionKey != null;
}

/** True when we can AES-encrypt outbound messages (view may still be locked). */
export function canEncryptChatMessages(): boolean {
  return sessionKey != null || readRememberedChatDecryptPassphrase() != null;
}

export async function openChatDecryptSession(
  passphrase: string,
  saltBase64: string,
): Promise<CryptoKey> {
  const key = await deriveChatCryptoKey(passphrase, saltBase64);
  setChatDecryptSession(key, saltBase64, passphrase);
  return key;
}

/**
 * Load encrypt key from the remembered passphrase without unlocking the view.
 * Returns null when the parent has never unlocked on this browser session.
 */
export async function ensureChatEncryptSession(saltBase64: string): Promise<CryptoKey | null> {
  if (sessionKey && sessionSalt === saltBase64) {
    return sessionKey;
  }
  const remembered = readRememberedChatDecryptPassphrase();
  if (!remembered) {
    return null;
  }
  return openChatDecryptSession(remembered, saltBase64);
}

export async function encodeOutgoingChatBody(
  plaintext: string,
  _saltBase64?: string | null,
): Promise<string> {
  if (outgoingBodyLeaksDecryptCode(plaintext)) {
    throw new Error('Decrypt code must not be sent in chat');
  }
  // Always seal as 5 random letters + word + 5 random letters so locked bubbles
  // show padded words (not AES base64 soup). Unlock strips the padding.
  const {encodeChatMessagePlaintext} = await import('./chatMessageObfuscation');
  return encodeChatMessagePlaintext(plaintext);
}

/** Decode for display. Locked view shows random-looking ciphertext, never plaintext. */
export async function decodeIncomingChatBody(
  body: string | null | undefined,
  unlocked: boolean,
): Promise<string> {
  if (!body) {
    return '';
  }
  const {decryptChatBody} = await import('./chatMessageCrypto');
  if (isEncryptedChatBody(body)) {
    if (!unlocked || !sessionKey) {
      return lockedChatBodyDisplay(body);
    }
    try {
      return await decryptChatBody(body, sessionKey);
    } catch {
      return lockedChatBodyDisplay(body);
    }
  }
  if (!unlocked) {
    return lockedChatBodyDisplay(body);
  }
  const {decodeChatMessagePlaintext} = await import('./chatMessageObfuscation');
  return decodeChatMessagePlaintext(body);
}
