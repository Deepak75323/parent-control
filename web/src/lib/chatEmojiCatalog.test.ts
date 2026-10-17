import {beforeEach, describe, expect, it, vi} from 'vitest';
import {defaultEmojiCategoryId, rememberEmoji} from './chatEmojiCatalog';
import {insertTextAtSelection} from './insertTextAtSelection';

function mockLocalStorage() {
  const store = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
  });
}

describe('insertTextAtSelection', () => {
  it('inserts emoji at the cursor', () => {
    expect(insertTextAtSelection('hi ', '😀', 3, 3)).toEqual({
      nextText: 'hi 😀',
      cursor: 5,
    });
  });

  it('replaces the current selection', () => {
    expect(insertTextAtSelection('hello world', '👍', 6, 11)).toEqual({
      nextText: 'hello 👍',
      cursor: 8,
    });
  });
});

describe('chatEmojiCatalog', () => {
  beforeEach(() => {
    mockLocalStorage();
  });

  it('defaults to smileys when there is no recent list', () => {
    expect(defaultEmojiCategoryId([])).toBe('smileys');
  });

  it('remembers recently used emoji', () => {
    const first = rememberEmoji('😀');
    expect(first[0]).toBe('😀');
    expect(defaultEmojiCategoryId(first)).toBe('recent');
  });
});
