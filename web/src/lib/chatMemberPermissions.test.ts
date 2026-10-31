import {describe, expect, it} from 'vitest';
import {
  canClearHistoryForEveryone,
  canDeleteNoteForEveryone,
  canGrantDecrypt,
  canMakeOwner,
  canMuteMember,
  canPurgeDeletedNoteFromReview,
  canRemoveMember,
  canTransferOwnership,
} from './chatMemberPermissions';

describe('chatMemberPermissions', () => {
  const owner = {userId: 'o', role: 'OWNER' as const, muted: false, canDecrypt: true};
  const admin = {userId: 'a', role: 'ADMIN' as const, muted: false, canDecrypt: false};
  const member = {userId: 'm', role: 'MEMBER' as const, muted: false, canDecrypt: false};

  it('lets owner and admin clear history', () => {
    expect(canClearHistoryForEveryone('OWNER')).toBe(true);
    expect(canClearHistoryForEveryone('ADMIN')).toBe(true);
    expect(canClearHistoryForEveryone('MEMBER')).toBe(false);
  });

  it('lets owner/admin delete any live note for everyone', () => {
    expect(canDeleteNoteForEveryone('OWNER', false)).toBe(true);
    expect(canDeleteNoteForEveryone('ADMIN', false)).toBe(true);
    expect(canDeleteNoteForEveryone('MEMBER', true)).toBe(true);
    expect(canDeleteNoteForEveryone('MEMBER', false)).toBe(false);
  });

  it('lets only the room owner purge deleted review notes', () => {
    expect(canPurgeDeletedNoteFromReview('OWNER', true)).toBe(true);
    expect(canPurgeDeletedNoteFromReview('ADMIN', false)).toBe(false);
    expect(canPurgeDeletedNoteFromReview('MEMBER', false)).toBe(false);
  });

  it('lets admin remove members but not other admins', () => {
    expect(canRemoveMember('ADMIN', member, 'a')).toBe(true);
    expect(canRemoveMember('ADMIN', admin, 'a')).toBe(false);
    expect(canRemoveMember('OWNER', admin, 'o')).toBe(true);
    expect(canRemoveMember('OWNER', owner, 'o')).toBe(false);
  });

  it('restricts mute and decrypt grant', () => {
    expect(canMuteMember('OWNER', member)).toBe(true);
    expect(canMuteMember('ADMIN', owner)).toBe(false);
    expect(canGrantDecrypt('ADMIN', member)).toBe(true);
  });

  it('only owner can transfer ownership', () => {
    expect(canTransferOwnership('OWNER')).toBe(true);
    expect(canMakeOwner('OWNER', member, 'o')).toBe(true);
    expect(canMakeOwner('ADMIN', member, 'a')).toBe(false);
  });
});
