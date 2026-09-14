import type {ChatMemberItem} from '../types/api';
import {formatPartnerPresenceLabel, type PartnerPresenceView} from './parentPresence';

export type MemberPresenceView = PartnerPresenceView & {
  userId: string;
};

export type MemberRosterEntry = {
  member: ChatMemberItem;
  presence: MemberPresenceView;
  /** Effective Discord-style status after applying visibility rules. */
  onlineVisible: boolean;
  statusLabel: string;
};

/** Hidden (visibility off) → always Offline with no last seen. */
export function effectiveMemberPresence(
  presence: MemberPresenceView | undefined,
  nowMs: number = Date.now(),
): {onlineVisible: boolean; statusLabel: string} {
  if (!presence || presence.hidden) {
    return {onlineVisible: false, statusLabel: 'Offline'};
  }
  if (presence.online) {
    return {onlineVisible: true, statusLabel: 'Online'};
  }
  const label = formatPartnerPresenceLabel(presence, nowMs);
  return {onlineVisible: false, statusLabel: label ?? 'Offline'};
}

export function buildMemberRoster(
  members: ChatMemberItem[],
  presenceByUserId: Record<string, MemberPresenceView>,
  nowMs: number = Date.now(),
): {online: MemberRosterEntry[]; offline: MemberRosterEntry[]} {
  const online: MemberRosterEntry[] = [];
  const offline: MemberRosterEntry[] = [];
  for (const member of members) {
    const presence = presenceByUserId[member.userId] ?? {
      userId: member.userId,
      online: false,
      lastSeenAt: null,
      hidden: true,
    };
    const effective = effectiveMemberPresence(presence, nowMs);
    const entry: MemberRosterEntry = {
      member,
      presence,
      onlineVisible: effective.onlineVisible,
      statusLabel: effective.statusLabel,
    };
    if (effective.onlineVisible) {
      online.push(entry);
    } else {
      offline.push(entry);
    }
  }
  const byName = (a: MemberRosterEntry, b: MemberRosterEntry) =>
    (a.member.nickname || a.member.label).localeCompare(b.member.nickname || b.member.label);
  online.sort(byName);
  offline.sort(byName);
  return {online, offline};
}
