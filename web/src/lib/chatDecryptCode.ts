/** Parent-selected 6-digit decrypt codes. Never store these in .env or send them in chat. */

export const CHAT_DECRYPT_CODE_LENGTH = 6;

export function normalizeDecryptCode(value: string): string {
  return value.trim();
}

export function isStrongChatDecryptCode(value: string): boolean {
  return /^\d{6}$/.test(normalizeDecryptCode(value));
}

/** True when the draft contains the live decrypt code (including without dashes). */
export function messageContainsDecryptCode(body: string, code: string): boolean {
  const needle = normalizeDecryptCode(code);
  if (!isStrongChatDecryptCode(needle)) {
    return false;
  }
  return body.includes(needle);
}
