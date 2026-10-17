import {afterEach, describe, expect, it} from 'vitest';
import {
  clearRememberedChatDecryptPassphrase,
  rememberChatDecryptPassphrase,
  readRememberedChatDecryptPassphrase,
} from './chatDecryptCredentialStore';

describe('chatDecryptCredentialStore', () => {
  afterEach(() => {
    clearRememberedChatDecryptPassphrase();
  });

  it('remembers and clears the decrypt passphrase', () => {
    rememberChatDecryptPassphrase('123456');
    expect(readRememberedChatDecryptPassphrase()).toBe('123456');
    clearRememberedChatDecryptPassphrase();
    expect(readRememberedChatDecryptPassphrase()).toBeNull();
  });
});
