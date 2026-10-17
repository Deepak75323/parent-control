import {describe, expect, it} from 'vitest';
import {countMergedEmojiLines, normalizeMergedEmojiArt} from './chatMergedEmojiFormat';

describe('normalizeMergedEmojiArt', () => {
  it('keeps art that already has one row per line', () => {
    const art = 'I Love You 💕1%\nI Love You 💕2%\nI Love You 💕3%';
    expect(normalizeMergedEmojiArt(art)).toBe(art);
  });

  it('splits flattened love-percent text onto separate rows', () => {
    const flat = 'I love you 1%❤ I love you 2%🧡 I love you 3%💛';
    expect(normalizeMergedEmojiArt(flat)).toBe(
      'I love you 1%❤\nI love you 2%🧡\nI love you 3%💛',
    );
  });

  it('preserves leading spaces on wave lines', () => {
    const wave = 'I love you 1%❤\n I love you 2%🧡\n  I love you 3%💛';
    expect(normalizeMergedEmojiArt(wave)).toBe(wave);
  });

  it('counts lines after normalization', () => {
    expect(countMergedEmojiLines('I love you 1%❤ I love you 2%🧡')).toBe(2);
  });
});
