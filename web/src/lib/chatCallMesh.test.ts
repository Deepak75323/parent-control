import {describe, expect, it} from 'vitest';
import {
  isPolitePeer,
  looksLikeUserId,
  normalizeCallUserId,
  remoteCallParticipantIds,
  resolveCallPeerLabel,
  sameCallUserId,
  shouldCreateOffer,
} from './chatCallMesh';

describe('chatCallMesh', () => {
  it('assigns polite role by UUID order', () => {
    expect(isPolitePeer('a', 'b')).toBe(true);
    expect(isPolitePeer('b', 'a')).toBe(false);
    expect(shouldCreateOffer('b', 'a')).toBe(true);
    expect(shouldCreateOffer('a', 'b')).toBe(false);
  });

  it('normalizes UUID case for identity checks', () => {
    expect(normalizeCallUserId(' AbC ')).toBe('abc');
    expect(
      sameCallUserId('AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE', 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'),
    ).toBe(true);
    expect(sameCallUserId('me', 'peer')).toBe(false);
  });

  it('excludes local user from remote peer tiles even when UUID case differs', () => {
    expect(
      remoteCallParticipantIds(
        [
          {userId: 'ME'},
          {userId: 'peer-a'},
          {userId: 'me'},
          {userId: 'PEER-A'},
          {userId: 'peer-b'},
        ],
        'Me',
      ),
    ).toEqual(['peer-a', 'peer-b']);
  });

  it('never uses raw user ids as call peer labels', () => {
    const id = 'c2fd6802-fe04-43cf-b888-06b6f9e56ae6';
    expect(looksLikeUserId(id)).toBe(true);
    expect(resolveCallPeerLabel(id, new Map())).toBe('Parent');
    expect(
      resolveCallPeerLabel(
        id,
        new Map([[id, 'Golden Lily']]),
      ),
    ).toBe('Golden Lily');
    expect(
      resolveCallPeerLabel(
        id,
        new Map([[id, id]]),
      ),
    ).toBe('Parent');
  });
});
