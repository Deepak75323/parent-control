import {describe, expect, it} from 'vitest';
import {
  advanceReadCursor,
  previousMessageId,
  unreadAfterCursor,
} from './chatUnreadMerge';

const messages = [
  {id: 'a', createdAt: '2026-09-04T02:00:00Z', unread: true},
  {id: 'b', createdAt: '2026-09-04T02:01:00Z', unread: true},
  {id: 'c', createdAt: '2026-09-04T02:02:00Z', unread: false},
  {id: 'me', createdAt: '2026-09-04T02:03:00Z', ownMessage: true, unread: false},
];

describe('unreadAfterCursor', () => {
  it('uses server unread flags when there is no cursor yet', () => {
    expect(unreadAfterCursor(messages, null).map(message => message.id)).toEqual(['a', 'b']);
  });

  it('counts only messages after the cursor', () => {
    expect(unreadAfterCursor(messages, 'a').map(message => message.id)).toEqual(['b', 'c']);
  });

  it('returns empty when caught up through the latest incoming', () => {
    expect(unreadAfterCursor(messages, 'c')).toEqual([]);
  });
});

describe('previousMessageId', () => {
  it('returns the prior message for mark-as-unread', () => {
    expect(previousMessageId(messages, 'c')).toBe('b');
    expect(previousMessageId(messages, 'a')).toBeNull();
  });
});

describe('advanceReadCursor', () => {
  it('does not move the cursor backwards', () => {
    expect(advanceReadCursor('c', 'a', messages)).toBe('c');
    expect(advanceReadCursor('a', 'c', messages)).toBe('c');
  });
});
