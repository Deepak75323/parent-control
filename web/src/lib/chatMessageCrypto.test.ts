import {beforeEach, describe, expect, it} from 'vitest';
import {
  decryptChatBody,
  deriveChatCryptoKey,
  encryptChatBody,
  isEncryptedChatBody,
} from './chatMessageCrypto';

describe('chatMessageCrypto', () => {
  const salt = btoa('0123456789abcdef');

  it('round-trips AES-GCM with PIN-derived key', async () => {
    const key = await deriveChatCryptoKey('supersecret', salt);
    const envelope = await encryptChatBody('hello parent', key);
    expect(isEncryptedChatBody(envelope)).toBe(true);
    expect(envelope.startsWith('pcp1:')).toBe(true);
    expect(await decryptChatBody(envelope, key)).toBe('hello parent');
  });

  it('fails decrypt with wrong passphrase', async () => {
    const good = await deriveChatCryptoKey('supersecret', salt);
    const bad = await deriveChatCryptoKey('wrongsecret', salt);
    const envelope = await encryptChatBody('secret text', good);
    await expect(decryptChatBody(envelope, bad)).rejects.toBeTruthy();
  });

  it('produces different ciphertext each encrypt (random IV)', async () => {
    const key = await deriveChatCryptoKey('supersecret', salt);
    const a = await encryptChatBody('same', key);
    const b = await encryptChatBody('same', key);
    expect(a).not.toBe(b);
  });
});
