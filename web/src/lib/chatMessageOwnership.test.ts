import {describe, expect, it} from 'vitest';
import {resolveOwnSenderUserId, withResolvedOwnership} from './chatMessageOwnership';
import type {ChatMessageItem} from '../types/api';

const base = (overrides: Partial<ChatMessageItem>): ChatMessageItem => ({
  id: 'm1',
  roomId: 'room-1',
  senderUserId: 'user-a',
  senderLabel: 'a@test.com',
  body: 'hi',
  createdAt: '2026-09-02T09:00:00.000Z',
  ...overrides,
});

describe('chatMessageOwnership', () => {
  it('resolves own sender id from API messages', () => {
    expect(
      resolveOwnSenderUserId([
        base({senderUserId: 'user-b', ownMessage: false}),
        base({senderUserId: 'user-a', ownMessage: true}),
      ]),
    ).toBe('user-a');
  });

  it('fixes wrong ownMessage from sender-scoped websocket payloads', () => {
    const fixed = withResolvedOwnership(
      base({senderUserId: 'user-b', ownMessage: true, receiptStatus: 'SENT'}),
      'user-a',
    );
    expect(fixed.ownMessage).toBe(false);
    expect(fixed.receiptStatus).toBeUndefined();
  });
});
