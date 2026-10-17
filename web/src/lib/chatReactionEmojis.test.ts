import {describe, expect, it} from 'vitest';
import {CHAT_QUICK_REACTION_EMOJIS, reactionTooltip} from './chatReactionEmojis';

describe('chatReactionEmojis', () => {
  it('exposes six quick reactions like WhatsApp', () => {
    expect(CHAT_QUICK_REACTION_EMOJIS).toEqual(['👍', '❤️', '😂', '😮', '😢', '🙏']);
  });

  it('builds a compact reactor tooltip', () => {
    expect(reactionTooltip(['You', 'Golden Lily'])).toBe('You, Golden Lily');
    expect(reactionTooltip(['A', 'B', 'C', 'D'])).toBe('A, B, C +1');
  });
});
