import {describe, expect, it} from 'vitest';
import {messageForOwnerDeletedView} from './ownerDeletedMessages';
import type {ChatMessageItem} from '../types/api';

function note(partial: Partial<ChatMessageItem> = {}): ChatMessageItem {
  return {
    id: 'm1',
    roomId: 'r1',
    senderUserId: 'u1',
    senderLabel: '@deep',
    body: 'hello',
    createdAt: '2026-09-26T00:00:00Z',
    ...partial,
  };
}

describe('messageForOwnerDeletedView', () => {
  it('keeps a live message', () => {
    const message = note();
    expect(
      messageForOwnerDeletedView(message, {
        removedForEveryone: false,
        showDeleted: false,
        viewerIsRoomOwner: true,
      }),
    ).toBe(message);
  });

  it('hides a deleted message from members', () => {
    expect(
      messageForOwnerDeletedView(note({deleted: true}), {
        removedForEveryone: true,
        showDeleted: true,
        viewerIsRoomOwner: false,
      }),
    ).toBeNull();
  });

  it('shows the deleted body to the room owner when they ask', () => {
    const shown = messageForOwnerDeletedView(note(), {
      removedForEveryone: true,
      showDeleted: true,
      viewerIsRoomOwner: true,
    });
    expect(shown?.deleted).toBe(true);
    expect(shown?.body).toBe('hello');
  });

  it('hides deleted messages from the owner until they ask', () => {
    expect(
      messageForOwnerDeletedView(note({deleted: true}), {
        removedForEveryone: false,
        showDeleted: false,
        viewerIsRoomOwner: true,
      }),
    ).toBeNull();
  });
});
