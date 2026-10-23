import {describe, expect, it} from 'vitest';
import {
  CHAT_STICKER_PACKS,
  defaultStickerPackId,
  isStickerOnlyMessage,
} from './chatStickerCatalog';

describe('chatStickerCatalog', () => {
  it('exposes non-empty sticker packs', () => {
    expect(CHAT_STICKER_PACKS.length).toBeGreaterThan(0);
    expect(CHAT_STICKER_PACKS.every(pack => pack.stickers.length > 0)).toBe(true);
    expect(defaultStickerPackId()).toBe(CHAT_STICKER_PACKS[0].id);
  });

  it('detects sticker-only notes', () => {
    expect(isStickerOnlyMessage('😂')).toBe(true);
    expect(isStickerOnlyMessage('  ❤️  ')).toBe(true);
    expect(isStickerOnlyMessage('hello 😂')).toBe(false);
    expect(isStickerOnlyMessage('')).toBe(false);
  });
});
