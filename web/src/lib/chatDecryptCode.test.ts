import {describe, expect, it} from 'vitest';
import {
  isStrongChatDecryptCode,
  messageContainsDecryptCode,
} from './chatDecryptCode';

describe('chatDecryptCode', () => {
  it('accepts exactly 6 digits', () => {
    expect(isStrongChatDecryptCode('987123')).toBe(true);
    expect(isStrongChatDecryptCode('1234')).toBe(false);
    expect(isStrongChatDecryptCode('1234567')).toBe(false);
    expect(isStrongChatDecryptCode('ABC123')).toBe(false);
  });

  it('detects the decrypt code inside a chat draft', () => {
    const code = '987123';
    expect(messageContainsDecryptCode(`here is ${code} secret`, code)).toBe(true);
    expect(messageContainsDecryptCode('hello parent', code)).toBe(false);
  });
});
