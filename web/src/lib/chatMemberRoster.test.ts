import {describe, expect, it} from 'vitest';
import {buildMemberRoster, effectiveMemberPresence} from './chatMemberRoster';

describe('effectiveMemberPresence', () => {
  it('treats hidden as always offline', () => {
    expect(
      effectiveMemberPresence({
        userId: '1',
        online: true,
        lastSeenAt: new Date().toISOString(),
        hidden: true,
      }),
    ).toEqual({onlineVisible: false, statusLabel: 'Offline'});
  });

  it('shows Online when visible and connected', () => {
    expect(
      effectiveMemberPresence({
        userId: '1',
        online: true,
        hidden: false,
      }),
    ).toEqual({onlineVisible: true, statusLabel: 'Online'});
  });
});

describe('buildMemberRoster', () => {
  const members = [
    {userId: 'a', label: 'Alice', role: 'OWNER' as const, joinedAt: ''},
    {userId: 'b', label: 'Bob', role: 'MEMBER' as const, joinedAt: ''},
  ];

  it('splits online and offline', () => {
    const {online, offline} = buildMemberRoster(members, {
      a: {userId: 'a', online: true, hidden: false},
      b: {userId: 'b', online: false, lastSeenAt: null, hidden: false},
    });
    expect(online.map(e => e.member.userId)).toEqual(['a']);
    expect(offline.map(e => e.member.userId)).toEqual(['b']);
  });
});
