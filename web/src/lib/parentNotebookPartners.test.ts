import {describe, expect, it} from 'vitest';
import {
  isTwoPersonNotebook,
  partnerDisplayName,
  partnerFromMembers,
} from './parentNotebookPartners';

describe('isTwoPersonNotebook', () => {
  it('matches only memberCount 2', () => {
    expect(isTwoPersonNotebook({memberCount: 2})).toBe(true);
    expect(isTwoPersonNotebook({memberCount: 1})).toBe(false);
    expect(isTwoPersonNotebook({memberCount: 3})).toBe(false);
  });
});

describe('partnerFromMembers', () => {
  const members = [
    {userId: 'me', label: 'Me', role: 'OWNER' as const, joinedAt: ''},
    {userId: 'them', label: 'Them', nickname: 'Grace', role: 'MEMBER' as const, joinedAt: ''},
  ];

  it('returns the other member', () => {
    expect(partnerFromMembers(members, 'me')?.userId).toBe('them');
  });

  it('falls back to first member without myUserId', () => {
    expect(partnerFromMembers(members, null)?.userId).toBe('me');
  });
});

describe('partnerDisplayName', () => {
  it('prefers nickname', () => {
    expect(
      partnerDisplayName({
        userId: '1',
        label: 'Golden Lily',
        nickname: 'Grace',
        role: 'MEMBER',
        joinedAt: '',
      }),
    ).toBe('Grace');
  });
});
