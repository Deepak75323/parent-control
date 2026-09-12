import {afterEach, describe, expect, it} from 'vitest';
import {clearRememberedChatDecryptPassphrase} from './chatDecryptCredentialStore';
import {
  canEncryptChatMessages,
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

  it('refuses to encrypt without blocking send — seals as random-looking legacy text', async () => {
    const sealed = await encodeOutgoingChatBody('hello');
    expect(sealed).not.toBe('hello');
    expect(sealed.startsWith('pcp1:')).toBe(false);
    expect(await decodeIncomingChatBody(sealed, false)).toBe(sealed);
  });

  it('shows random-looking text while locked, not a plaintext label', async () => {
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

  it('round-trips AES after unlock', async () => {
    const key = await openChatDecryptSession('987123', salt);
    const sealed = await encodeOutgoingChatBody('parent only');
    expect(sealed.startsWith('pcp1:')).toBe(true);
    expect(await decodeIncomingChatBody(sealed, true)).toBe('parent only');
    expect(key).toBeTruthy();
  });

  it('can encrypt after lock view if passphrase was remembered', async () => {
    await openChatDecryptSession('987123', salt);
    clearChatDecryptSession();
    expect(canEncryptChatMessages()).toBe(true);
    const key = await ensureChatEncryptSession(salt);
    expect(key).toBeTruthy();
    const sealed = await encodeOutgoingChatBody('still encrypted', salt);
    expect(sealed.startsWith('pcp1:')).toBe(true);
    // View stays locked — gibberish, not plaintext.
    expect(await decodeIncomingChatBody(sealed, false)).not.toBe('still encrypted');
  });

  it('refuses to encrypt a body that contains the decrypt code', async () => {
    const code = '987123';
    await openChatDecryptSession(code, salt);
    await expect(encodeOutgoingChatBody(`do not share ${code}`)).rejects.toThrow(
      /must not be sent in chat/,
    );
  });
});
