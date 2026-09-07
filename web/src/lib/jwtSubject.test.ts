import {describe, expect, it} from 'vitest';
import {userIdFromAccessToken} from './jwtSubject';

function fakeJwt(sub: string): string {
  const header = btoa(JSON.stringify({alg: 'none'}));
  const payload = btoa(JSON.stringify({sub}));
  return `${header}.${payload}.sig`;
}

describe('userIdFromAccessToken', () => {
  it('reads sub from JWT payload', () => {
    expect(userIdFromAccessToken(fakeJwt('user-123'))).toBe('user-123');
  });

  it('returns null for invalid tokens', () => {
    expect(userIdFromAccessToken(null)).toBeNull();
    expect(userIdFromAccessToken('not-a-jwt')).toBeNull();
  });
});
