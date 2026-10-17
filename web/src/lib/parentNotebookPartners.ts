import type {ChatMemberItem, ChatRoomSummary} from '../types/api';

/** Notebooks / rooms shared with exactly one other parent. */
export function isTwoPersonNotebook(
  room: Pick<ChatRoomSummary, 'memberCount'>,
): boolean {
  return room.memberCount === 2;
}

export function partnerFromMembers(
  members: ChatMemberItem[],
  myUserId: string | null | undefined,
): ChatMemberItem | null {
  if (members.length === 0) {
    return null;
  }
  if (!myUserId) {
    return members[0] ?? null;
  }
  return members.find(member => member.userId !== myUserId) ?? null;
}

export function partnerDisplayName(partner: ChatMemberItem | null): string {
  if (!partner) {
    return 'Parent';
  }
  const nick = partner.nickname?.trim();
  if (nick) {
    return nick;
  }
  return partner.label?.trim() || 'Parent';
}
