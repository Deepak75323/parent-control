import {afterEach, describe, expect, it} from 'vitest';
import {
  clearChatDecryptSession,
  decodeIncomingChatBody,
  encodeOutgoingChatBody,
  ensureChatEncryptSession,
  lockedChatBodyDisplay,
  openChatDecryptSession,
  wipeChatDecryptCredentials,
} from './chatDecryptSession';
import {encryptChatBody} from './chatMessageCrypto';
import {encodeChatMessagePlaintext} from './chatMessageObfuscation';

describe('chatDecryptSession', () => {
  const salt = btoa('0123456789abcdef');

  afterEach(() => {
    wipeChatDecryptCredentials();
  });

  it('seals outbound as three random letters around each word', async () => {
    const sealed = await encodeOutgoingChatBody('hi');
    expect(sealed).toMatch(/^[a-z]{3}hi[a-z]{3}$/);
    expect(await decodeIncomingChatBody(sealed, false)).toBe(sealed);
    expect(await decodeIncomingChatBody(sealed, true)).toBe('hi');
  });

  it('shows padded words while locked, not a plaintext label', async () => {
    const obfuscated = encodeChatMessagePlaintext('secret words', {seed: 's'});
    const lockedLegacy = await decodeIncomingChatBody(obfuscated, false);
    expect(lockedLegacy).toBe(obfuscated);
    expect(lockedLegacy).not.toMatch(/Encrypted message/i);

    await openChatDecryptSession('987123', salt);
    const {getChatDecryptKey} = await import('./chatDecryptSession');
    const envelope = await encryptChatBody('hidden text', getChatDecryptKey()!);
    clearChatDecryptSession();
    const lockedAes = await decodeIncomingChatBody(envelope, false);
    expect(lockedAes).toBe(lockedChatBodyDisplay(envelope));
    expect(lockedAes).not.toMatch(/Encrypted message/i);
    expect(lockedAes).not.toMatch(/pcp1:/);
    expect(lockedAes.length).toBeGreaterThan(8);
  });

  it('round-trips padded outbound after unlock', async () => {
    const key = await openChatDecryptSession('987123', salt);
    const sealed = await encodeOutgoingChatBody('parent only');
    expect(sealed.startsWith('pcp1:')).toBe(false);
    expect(sealed).toMatch(/^[a-z]{3}parent[a-z]{3} [a-z]{3}only[a-z]{3}$/);
    expect(await decodeIncomingChatBody(sealed, true)).toBe('parent only');
    expect(key).toBeTruthy();
  });

  it('can restore encrypt session from remembered passphrase without unlocking view', async () => {
    await openChatDecryptSession('987123', salt);
    clearChatDecryptSession();
    const key = await ensureChatEncryptSession(salt);
    expect(key).toBeTruthy();
    const sealed = await encodeOutgoingChatBody('still padded', salt);
    expect(sealed.startsWith('pcp1:')).toBe(false);
    expect(await decodeIncomingChatBody(sealed, false)).not.toBe('still padded');
    expect(await decodeIncomingChatBody(sealed, true)).toBe('still padded');
  });

  it('refuses to seal a body that contains the decrypt code', async () => {
    const code = '987123';
    await openChatDecryptSession(code, salt);
    await expect(encodeOutgoingChatBody(`do not share ${code}`)).rejects.toThrow(
      /must not be sent in chat/,
    );
  });
});
