/** Pure helpers for chat room member moderation UI. */

export type ChatMemberRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export type ChatMemberPermissionsView = {
  userId: string;
  role: ChatMemberRole;
  muted: boolean;
  canDecrypt: boolean;
};

export function isModerator(role: ChatMemberRole | undefined | null): boolean {
  return role === 'OWNER' || role === 'ADMIN';
}

export function canManageMembers(myRole: ChatMemberRole | undefined | null): boolean {
  return isModerator(myRole);
}

export function canClearHistoryForEveryone(myRole: ChatMemberRole | undefined | null): boolean {
  return isModerator(myRole);
}

export function canTransferOwnership(myRole: ChatMemberRole | undefined | null): boolean {
  return myRole === 'OWNER';
}

export function canChangeRoles(myRole: ChatMemberRole | undefined | null): boolean {
  return myRole === 'OWNER';
}

export function canRemoveMember(
  myRole: ChatMemberRole | undefined | null,
  target: Pick<ChatMemberPermissionsView, 'role' | 'userId'>,
  myUserId: string | undefined,
): boolean {
  if (!isModerator(myRole) || !myUserId || target.userId === myUserId) {
    return false;
  }
  if (target.role === 'OWNER') {
    return false;
  }
  if (myRole === 'ADMIN' && target.role !== 'MEMBER') {
    return false;
  }
  return true;
}

export function canMuteMember(
  myRole: ChatMemberRole | undefined | null,
  target: Pick<ChatMemberPermissionsView, 'role'>,
): boolean {
  if (!isModerator(myRole)) {
    return false;
  }
  if (target.role === 'OWNER') {
    return false;
  }
  if (myRole === 'ADMIN' && target.role !== 'MEMBER') {
    return false;
  }
  return true;
}

export function canGrantDecrypt(
  myRole: ChatMemberRole | undefined | null,
  target: Pick<ChatMemberPermissionsView, 'role'>,
): boolean {
  return canMuteMember(myRole, target);
}

export function canMakeOwner(
  myRole: ChatMemberRole | undefined | null,
  target: Pick<ChatMemberPermissionsView, 'role' | 'userId'>,
  myUserId: string | undefined,
): boolean {
  return (
    canTransferOwnership(myRole) &&
    Boolean(myUserId) &&
    target.userId !== myUserId &&
    target.role !== 'OWNER'
  );
}
