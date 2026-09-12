import {afterEach, describe, expect, it} from 'vitest';
import {
  clearChatDecryptSession,
  decodeIncomingChatBody,
  encodeOutgoingChatBody,
  LOCKED_CHAT_BODY,
  openChatDecryptSession,
} from './chatDecryptSession';
import {encryptChatBody} from './chatMessageCrypto';
import {encodeChatMessagePlaintext} from './chatMessageObfuscation';

describe('chatDecryptSession', () => {
  const salt = btoa('0123456789abcdef');

  afterEach(() => {
    clearChatDecryptSession();
  });

  it('refuses to encrypt without an unlocked session', async () => {
    await expect(encodeOutgoingChatBody('hello')).rejects.toThrow(/Decrypt session required/);
  });

  it('hides AES and legacy bodies until unlocked', async () => {
    const obfuscated = encodeChatMessagePlaintext('secret words', {seed: 's'});
    expect(await decodeIncomingChatBody(obfuscated, false)).toBe(LOCKED_CHAT_BODY);

    await openChatDecryptSession('987123', salt);
    const {getChatDecryptKey} = await import('./chatDecryptSession');
    const envelope = await encryptChatBody('hidden text', getChatDecryptKey()!);
    clearChatDecryptSession();
    expect(await decodeIncomingChatBody(envelope, false)).toBe(LOCKED_CHAT_BODY);
  });

  it('round-trips AES after unlock', async () => {
    const key = await openChatDecryptSession('987123', salt);
    const sealed = await encodeOutgoingChatBody('parent only');
    expect(sealed.startsWith('pcp1:')).toBe(true);
    expect(await decodeIncomingChatBody(sealed, true)).toBe('parent only');
    expect(key).toBeTruthy();
  });

  it('refuses to encrypt a body that contains the decrypt code', async () => {
    const code = '987123';
    await openChatDecryptSession(code, salt);
    await expect(encodeOutgoingChatBody(`do not share ${code}`)).rejects.toThrow(
      /must not be sent in chat/,
    );
  });
});
