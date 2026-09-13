import {describe, expect, it} from 'vitest';
import {isDirectStyleRoom} from './ChatRoomsPanel';
import type {ChatRoomSummary} from '../../types/api';

function room(memberCount: number): ChatRoomSummary {
  return {
    id: 'r1',
    name: 'Test',
    ownerUserId: 'u1',
    owner: false,
    myRole: 'MEMBER',
    muted: false,
    canDecrypt: false,
    memberCount,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    unreadCount: 0,
  };
}

describe('isDirectStyleRoom', () => {
  it('treats exactly two members as direct when flag absent', () => {
    expect(isDirectStyleRoom(room(2))).toBe(true);
  });

  it('treats group and solo rooms as rooms section', () => {
    expect(isDirectStyleRoom(room(1))).toBe(false);
    expect(isDirectStyleRoom(room(3))).toBe(false);
  });

  it('prefers directMessage flag over member count', () => {
    expect(isDirectStyleRoom({...room(2), directMessage: false, name: 'Notes'})).toBe(false);
    expect(isDirectStyleRoom({...room(2), directMessage: true, name: 'Friend'})).toBe(true);
  });
});
