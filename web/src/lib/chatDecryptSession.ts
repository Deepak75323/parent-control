import {messageContainsDecryptCode} from './chatDecryptCode';
import {deriveChatCryptoKey, encryptChatBody} from './chatMessageCrypto';

export const LOCKED_CHAT_BODY = '🔒 Encrypted message';

let sessionKey: CryptoKey | null = null;
let sessionSalt: string | null = null;
let sessionPassphrase: string | null = null;

export function clearChatDecryptSession(): void {
  sessionKey = null;
  sessionSalt = null;
  sessionPassphrase = null;
}

export function setChatDecryptSession(key: CryptoKey, salt: string, passphrase?: string): void {
  sessionKey = key;
  sessionSalt = salt;
  if (passphrase) {
    sessionPassphrase = passphrase;
  }
}

export function outgoingBodyLeaksDecryptCode(plaintext: string): boolean {
  if (!sessionPassphrase) {
    return false;
  }
  return messageContainsDecryptCode(plaintext, sessionPassphrase);
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

export async function openChatDecryptSession(
  passphrase: string,
  saltBase64: string,
): Promise<CryptoKey> {
  const key = await deriveChatCryptoKey(passphrase, saltBase64);
  setChatDecryptSession(key, saltBase64, passphrase);
  return key;
}

export async function encodeOutgoingChatBody(plaintext: string): Promise<string> {
  if (!sessionKey) {
    throw new Error('Decrypt session required to encrypt chat');
  }
  if (outgoingBodyLeaksDecryptCode(plaintext)) {
    throw new Error('Decrypt code must not be sent in chat');
  }
  return encryptChatBody(plaintext, sessionKey);
}

/** Decode for display. Locked view never returns stored ciphertext or legacy padding. */
export async function decodeIncomingChatBody(
  body: string | null | undefined,
  unlocked: boolean,
): Promise<string> {
  if (!body) {
    return '';
  }
  const {isEncryptedChatBody, decryptChatBody} = await import('./chatMessageCrypto');
  if (isEncryptedChatBody(body)) {
    if (!unlocked || !sessionKey) {
      return LOCKED_CHAT_BODY;
    }
    try {
      return await decryptChatBody(body, sessionKey);
    } catch {
      return LOCKED_CHAT_BODY;
    }
  }
  if (!unlocked) {
    return LOCKED_CHAT_BODY;
  }
  const {decodeChatMessagePlaintext} = await import('./chatMessageObfuscation');
  return decodeChatMessagePlaintext(body);
}
