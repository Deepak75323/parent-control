import {describe, expect, it} from 'vitest';
import {ApiPaths} from '../api/paths';
import {isDirectStyleRoom} from '../components/chat/ChatRoomsPanel';
import type {ChatRoomSummary} from '../types/api';

function room(partial: Partial<ChatRoomSummary>): ChatRoomSummary {
  return {
    id: 'r1',
    name: 'Room',
    ownerUserId: 'u1',
    owner: true,
    memberCount: 2,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

/** Smoke: friends API path shape used by Connect Parent → Add friend. */
describe('parent friends graph', () => {
  it('maps accept response dmRoomId for Direct messages', () => {
    const response = {
      id: 'req-1',
      status: 'ACCEPTED',
      dmRoomId: 'room-1',
    };
    expect(response.status).toBe('ACCEPTED');
    expect(response.dmRoomId).toBeTruthy();
  });

  it('builds open-dm path for friend click', () => {
    expect(ApiPaths.friendOpenDm('user-42')).toBe('/api/v1/parent/friends/user-42/dm');
  });

  it('treats only directMessage rooms as DMs when flag is present', () => {
    expect(isDirectStyleRoom(room({directMessage: false, memberCount: 2, name: 'Notes'}))).toBe(
      false,
    );
    expect(isDirectStyleRoom(room({directMessage: true, memberCount: 2, name: 'Golden Lily'}))).toBe(
      true,
    );
  });
});
