import {describe, expect, it} from 'vitest';
import {withMonotonicReceiptStatuses} from './chatReceiptMonotonic';
import type {ChatMessageItem} from '../types/api';

function own(id: string, receiptStatus: ChatMessageItem['receiptStatus']): ChatMessageItem {
  return {
    id,
    roomId: 'room-1',
    senderUserId: 'me',
    senderLabel: 'me',
    body: id,
    ownMessage: true,
    receiptStatus,
    createdAt: '2026-09-02T10:38:00.000Z',
  };
}

describe('withMonotonicReceiptStatuses', () => {
  it('upgrades earlier sent messages when a later one is read', () => {
    const messages = [own('a', 'SENT'), own('b', 'READ')];
    const fixed = withMonotonicReceiptStatuses(messages);
    expect(fixed[0]?.receiptStatus).toBe('READ');
    expect(fixed[1]?.receiptStatus).toBe('READ');
  });
});
