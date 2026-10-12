import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  BUILTIN_MERGED_EMOJI_COLLECTIONS,
  addCustomMergedEmoji,
  loadCustomMergedEmojis,
  mergedEmojiFitsLimit,
  previewMergedEmoji,
  previewMergedEmojiLines,
  removeCustomMergedEmoji,
} from './chatMergedEmojiCatalog';
import {CHAT_MESSAGE_PLAINTEXT_MAX} from './chatMessageLimits';

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

describe('chatMergedEmojiCatalog', () => {
  beforeEach(() => {
    mockLocalStorage();
  });

  it('ships built-in love and heart merged arts', () => {
    const labels = BUILTIN_MERGED_EMOJI_COLLECTIONS.flatMap(collection =>
      collection.items.map(item => item.label),
    );
    expect(labels).toEqual(
      expect.arrayContaining(['Love percent wave', 'Nested heart', 'Cat sending heart']),
    );
  });

  it('rejects arts that exceed the note length limit', () => {
    expect(mergedEmojiFitsLimit('ok')).toBe(true);
    expect(mergedEmojiFitsLimit('x'.repeat(CHAT_MESSAGE_PLAINTEXT_MAX + 1))).toBe(false);
  });

  it('saves a custom merged emoji into My collection', () => {
    const saved = addCustomMergedEmoji({
      label: 'Golden lily',
      art: '🌻\nGolden Lily',
      tags: ['golden lily', 'cute'],
    });
    expect(saved.label).toBe('Golden lily');
    expect(loadCustomMergedEmojis()).toHaveLength(1);
    expect(loadCustomMergedEmojis()[0]?.art).toBe('🌻\nGolden Lily');
  });

  it('removes a custom merged emoji', () => {
    const saved = addCustomMergedEmoji({
      label: 'Temp',
      art: '💛',
      tags: ['heart'],
    });
    removeCustomMergedEmoji(saved.id);
    expect(loadCustomMergedEmojis()).toHaveLength(0);
  });

  it('builds a short single-line preview', () => {
    expect(previewMergedEmoji('line1\nline2\nline3')).toContain('line1');
    expect(previewMergedEmoji('line1\nline2\nline3')).not.toContain('\n');
  });

  it('keeps line order in multi-line preview', () => {
    const art = 'I Love You 💕1%\nI Love You 💕2%\nI Love You 💕3%';
    expect(previewMergedEmojiLines(art)).toBe(art);
  });
});
