import {describe, expect, it} from 'vitest';
import {shouldShowMembersRoster} from './membersPanelVisibility';

describe('shouldShowMembersRoster', () => {
  it('always shows for the room owner', () => {
    expect(shouldShowMembersRoster({isOwner: true, membersPanelShared: false})).toBe(true);
    expect(shouldShowMembersRoster({isOwner: true, membersPanelShared: true})).toBe(true);
  });

  it('hides from non-owners when the owner has not shared', () => {
    expect(shouldShowMembersRoster({isOwner: false, membersPanelShared: false})).toBe(false);
  });

  it('shows to non-owners when the owner has shared', () => {
    expect(shouldShowMembersRoster({isOwner: false, membersPanelShared: true})).toBe(true);
  });
});
