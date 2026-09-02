import {describe, expect, it} from 'vitest';
import {chatMessageElementId, computeCenteredScrollTop} from './chatScrollToMessage';

describe('chatScrollToMessage', () => {
  it('builds stable message element ids', () => {
    expect(chatMessageElementId('abc-123')).toBe('chat-message-abc-123');
  });

  it('centers a message inside the scroll container', () => {
    expect(
      computeCenteredScrollTop(
        0,
        400,
        100,
        500,
        40,
      ),
    ).toBe(220);
  });

  it('never scrolls above the top of the message list', () => {
    expect(
      computeCenteredScrollTop(
        0,
        400,
        100,
        120,
        40,
      ),
    ).toBe(0);
  });
});
