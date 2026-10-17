import {describe, expect, it} from 'vitest';
import {formatChatTypingLine} from './chatTypingLabel';

describe('formatChatTypingLine', () => {
  it('formats a single typer', () => {
    expect(
      formatChatTypingLine([
        {userId: '1', nickname: 'Graceful Harbor', label: 'a@b.com', expiresAt: 1},
      ]),
    ).toBe('Graceful Harbor is typing…');
  });

  it('formats two typers', () => {
    expect(
      formatChatTypingLine([
        {userId: '1', nickname: 'Ada', label: 'a', expiresAt: 1},
        {userId: '2', nickname: 'Bea', label: 'b', expiresAt: 1},
      ]),
    ).toBe('Ada and Bea are typing…');
  });

  it('collapses three or more typers', () => {
    expect(
      formatChatTypingLine([
        {userId: '1', nickname: 'A', label: 'a', expiresAt: 1},
        {userId: '2', nickname: 'B', label: 'b', expiresAt: 1},
        {userId: '3', nickname: 'C', label: 'c', expiresAt: 1},
      ]),
    ).toBe('Several people are typing…');
  });
});
