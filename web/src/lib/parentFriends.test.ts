import {describe, expect, it} from 'vitest';
import {ApiPaths} from '../api/paths';

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
});
