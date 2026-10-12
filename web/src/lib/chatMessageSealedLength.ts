import {encodeChatMessagePlaintext} from './chatMessageObfuscation';
import {CHAT_MESSAGE_BODY_MAX} from './chatMessageLimits';

const LENGTH_ESTIMATE_SEED = 'chat-sealed-length-estimate';

/** Deterministic sealed length (obfuscation padding is always 3 letters per side). */
export function sealedChatBodyLength(plaintext: string): number {
  if (!plaintext.trim()) {
    return 0;
  }
  return encodeChatMessagePlaintext(plaintext, {seed: LENGTH_ESTIMATE_SEED}).length;
}

export function plaintextFitsSealedLimit(
  plaintext: string,
  maxSealed = CHAT_MESSAGE_BODY_MAX,
): boolean {
  return sealedChatBodyLength(plaintext) <= maxSealed;
}

/** Drop trailing lines until the sealed payload fits the server limit. */
export function trimPlaintextToSealedLimit(
  plaintext: string,
  maxSealed = CHAT_MESSAGE_BODY_MAX,
): string {
  if (plaintextFitsSealedLimit(plaintext, maxSealed)) {
    return plaintext;
  }
  const lines = plaintext.split('\n');
  while (lines.length > 0 && !plaintextFitsSealedLimit(lines.join('\n'), maxSealed)) {
    lines.pop();
  }
  return lines.join('\n');
}

export function chatMessageTooLongAfterSealingError(plaintext: string): string {
  const sealed = sealedChatBodyLength(plaintext);
  return `Message is too long after sealing (${sealed.toLocaleString()} characters, max ${CHAT_MESSAGE_BODY_MAX.toLocaleString()}). Try fewer lines or shorter words.`;
}
