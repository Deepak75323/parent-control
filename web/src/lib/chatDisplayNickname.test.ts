import {describe, expect, it} from 'vitest';
import {chatDisplayNickname, resolveChatDisplayNickname} from './chatDisplayNickname';

describe('chatDisplayNickname', () => {
  it('returns a stable nickname for the same email', () => {
    const first = chatDisplayNickname('anilkumar@gmail.com');
    const second = chatDisplayNickname('anilkumar@gmail.com');
    expect(first).toBe(second);
  });

  it('is case-insensitive and trims whitespace', () => {
    expect(chatDisplayNickname('AnilKumar@gmail.com')).toBe(
      chatDisplayNickname('  anilkumar@gmail.com  '),
    );
  });

  it('uses two beautiful capitalized words', () => {
    const nickname = chatDisplayNickname('parent@local.test');
    expect(nickname).not.toContain('@');
    expect(nickname).toMatch(/^[A-Z][a-z]+ [A-Z][a-z]+$/);
  });

  it('usually differs for different emails', () => {
    const a = chatDisplayNickname('parent-a@local.test');
    const b = chatDisplayNickname('parent-b@local.test');
    expect(a).not.toBe(b);
  });

  it('prefers server nickname when provided', () => {
    expect(resolveChatDisplayNickname('Golden Aurora', 'other@local.test')).toBe('Golden Aurora');
  });

  it('derives from email when nickname is missing', () => {
    expect(resolveChatDisplayNickname(undefined, 'parent@local.test')).toBe(
      chatDisplayNickname('parent@local.test'),
    );
  });
});
