/** Owner always sees the Members roster; others only when the owner shares it. */
export function shouldShowMembersRoster(opts: {
  isOwner: boolean;
  membersPanelShared: boolean;
}): boolean {
  return opts.isOwner || opts.membersPanelShared;
}
