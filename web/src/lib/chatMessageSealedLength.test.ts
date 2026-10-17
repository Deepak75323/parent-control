import {describe, expect, it} from 'vitest';
import {BUILTIN_MERGED_EMOJI_COLLECTIONS} from './chatMergedEmojiCatalog';
import {
  plaintextFitsSealedLimit,
  sealedChatBodyLength,
  trimPlaintextToSealedLimit,
} from './chatMessageSealedLength';
import {CHAT_MESSAGE_BODY_MAX} from './chatMessageLimits';

describe('sealedChatBodyLength', () => {
  it('is longer than plaintext for multi-word lines', () => {
    const plain = 'I love you 1%❤\nI love you 2%🧡';
    expect(sealedChatBodyLength(plain)).toBeGreaterThan(plain.length);
  });

  it('fits built-in love percent list within body max', () => {
    const list = BUILTIN_MERGED_EMOJI_COLLECTIONS.flatMap(c => c.items).find(
      item => item.id === 'love-percent-straight',
    );
    expect(list).toBeDefined();
    expect(plaintextFitsSealedLimit(list!.art)).toBe(true);
    expect(sealedChatBodyLength(list!.art)).toBeLessThanOrEqual(CHAT_MESSAGE_BODY_MAX);
  });

  it('trims trailing lines when sealed payload would exceed max', () => {
    const huge = 'word '.repeat(9000).trim();
    const trimmed = trimPlaintextToSealedLimit(huge, 500);
    expect(trimmed.length).toBeLessThan(huge.length);
    expect(sealedChatBodyLength(trimmed)).toBeLessThanOrEqual(500);
  });
});
