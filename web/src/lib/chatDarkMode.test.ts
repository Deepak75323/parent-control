import {describe, expect, it} from 'vitest';
import {
  chatDarkStorageValue,
  isChatDarkMode,
  readChatDarkMode,
  setChatDarkMode,
  subscribeChatDarkMode,
} from './chatDarkMode';

describe('chat dark mode', () => {
  it('turns on only when the saved value is enabled', () => {
    expect(readChatDarkMode('1')).toBe(true);
    expect(readChatDarkMode('0')).toBe(false);
    expect(readChatDarkMode(null)).toBe(false);
  });

  it('stores the choice as a flag the next visit can read', () => {
    expect(chatDarkStorageValue(true)).toBe('1');
    expect(chatDarkStorageValue(false)).toBe('0');
    expect(readChatDarkMode(chatDarkStorageValue(true))).toBe(true);
  });

  it('notifies the chat page when the parent switches mode', () => {
    const seen: boolean[] = [];
    const stop = subscribeChatDarkMode(() => seen.push(isChatDarkMode()));
    const previous = isChatDarkMode();
    setChatDarkMode(!previous);
    expect(seen).toEqual([!previous]);
    setChatDarkMode(previous);
    stop();
  });
});
