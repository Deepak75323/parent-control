import {useMemo, useState} from 'react';
import {resolveChatDisplayNickname} from '../../lib/chatDisplayNickname';
import {
  canChangeRoles,
  canGrantDecrypt,
  canMakeOwner,
  canManageMembers,
  canMuteMember,
  canRemoveMember,
  type ChatMemberRole,
} from '../../lib/chatMemberPermissions';
import type {ChatMemberItem} from '../../types/api';

type Props = {
  open: boolean;
  roomName: string;
  members: ChatMemberItem[];
  myUserId?: string;
  myRole?: ChatMemberRole;
  busy?: boolean;
  onClose: () => void;
  onRemove: (userId: string) => Promise<void>;
  onUpdate: (
    userId: string,
    patch: {role?: 'ADMIN' | 'MEMBER'; muted?: boolean; canDecrypt?: boolean},
  ) => Promise<void>;
  onTransferOwnership: (userId: string) => Promise<void>;
};

function memberLabel(member: ChatMemberItem): string {
  return resolveChatDisplayNickname(member.nickname, member.label);
}

function noActionReason(
  myRole: ChatMemberRole | undefined,
  member: ChatMemberItem,
  myUserId: string | undefined,
): string | null {
  if (member.userId === myUserId) {
    return 'This is you';
  }
  if (!canManageMembers(myRole)) {
    return 'Only the owner or an admin can manage members';
  }
  if (member.role === 'OWNER') {
    return 'The server owner cannot be muted or removed';
  }
  if (myRole === 'ADMIN' && member.role === 'ADMIN') {
    return 'Admins cannot manage other admins';
  }
  return null;
}

export function ChatMembersManageDialog({
  open,
  roomName,
  members,
  myUserId,
  myRole,
  busy = false,
  onClose,
  onRemove,
  onUpdate,
  onTransferOwnership,
}: Props) {
  const [error, setError] = useState<string | null>(null);
  const [actingUserId, setActingUserId] = useState<string | null>(null);
  const [expandedUserId, setExpandedUserId] = useState<string | null>(null);

  const isModerator = canManageMembers(myRole);

  const sortedMembers = useMemo(() => {
    const rank = (role: string) => (role === 'OWNER' ? 0 : role === 'ADMIN' ? 1 : 2);
    return [...members].sort((a, b) => rank(a.role) - rank(b.role));
  }, [members]);

  if (!open) {
    return null;
  }

  async function run(userId: string, action: () => Promise<void>) {
    setError(null);
    setActingUserId(userId);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Action failed');
    } finally {
      setActingUserId(null);
    }
  }

  return (
    <div className="confirm-dialog-overlay" role="presentation" onClick={onClose}>
      <div
        className="confirm-dialog chat-members-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="chat-members-title"
        onClick={e => e.stopPropagation()}>
        <h2 id="chat-members-title" className="confirm-dialog-title">
          Members · {roomName}
        </h2>
        <p className="muted tiny">
          {isModerator
            ? 'Click a member to mute, change role, grant decrypt, or remove.'
            : 'You are a member — ask the owner or an admin to change roles or remove people.'}
        </p>
        {error ? <p className="chat-decrypt-pin-error">{error}</p> : null}
        <ul className="chat-members-list">
          {sortedMembers.map(member => {
            const muted = Boolean(member.muted);
            const canDecrypt = Boolean(member.canDecrypt);
            const rowBusy = busy || actingUserId === member.userId;
            const isYou = member.userId === myUserId;
            const expanded = expandedUserId === member.userId;
            const reason = noActionReason(myRole, member, myUserId);
            const actions = {
              mute: canMuteMember(myRole, member),
              decrypt: canGrantDecrypt(myRole, member),
              makeAdmin: canChangeRoles(myRole) && member.role === 'MEMBER',
              removeAdmin: canChangeRoles(myRole) && member.role === 'ADMIN',
              makeOwner: canMakeOwner(myRole, member, myUserId),
              remove: canRemoveMember(myRole, member, myUserId),
            };
            const hasActions = Object.values(actions).some(Boolean);

            return (
              <li
                key={member.userId}
                className={`chat-members-row${expanded ? ' chat-members-row--expanded' : ''}${
                  isYou ? ' chat-members-row--you' : ''
                }`}>
                <button
                  type="button"
                  className="chat-members-row-select"
                  aria-expanded={expanded}
                  onClick={() =>
                    setExpandedUserId(prev => (prev === member.userId ? null : member.userId))
                  }>
                  <span className="chat-members-row-main">
                    <strong>{memberLabel(member)}</strong>
                    <span className="muted tiny">
                      {member.role}
                      {muted ? ' · muted' : ''}
                      {canDecrypt ? ' · decrypt' : ''}
                      {isYou ? ' · you' : ''}
                    </span>
                  </span>
                  <span className="chat-members-row-caret" aria-hidden>
                    {expanded ? '▾' : '▸'}
                  </span>
                </button>

                {expanded ? (
                  <div className="chat-members-row-actions">
                    {hasActions ? (
                      <>
                        {actions.mute ? (
                          <button
                            type="button"
                            className="btn ghost tiny"
                            disabled={rowBusy}
                            onClick={() =>
                              void run(member.userId, () =>
                                onUpdate(member.userId, {muted: !muted}),
                              )
                            }>
                            {muted ? 'Unmute' : 'Mute'}
                          </button>
                        ) : null}
                        {actions.decrypt ? (
                          <button
                            type="button"
                            className="btn ghost tiny"
                            disabled={rowBusy}
                            onClick={() =>
                              void run(member.userId, () =>
                                onUpdate(member.userId, {canDecrypt: !canDecrypt}),
                              )
                            }>
                            {canDecrypt ? 'Revoke decrypt' : 'Allow decrypt'}
                          </button>
                        ) : null}
                        {actions.makeAdmin ? (
                          <button
                            type="button"
                            className="btn ghost tiny"
                            disabled={rowBusy}
                            onClick={() =>
                              void run(member.userId, () =>
                                onUpdate(member.userId, {role: 'ADMIN'}),
                              )
                            }>
                            Make admin
                          </button>
                        ) : null}
                        {actions.removeAdmin ? (
                          <button
                            type="button"
                            className="btn ghost tiny"
                            disabled={rowBusy}
                            onClick={() =>
                              void run(member.userId, () =>
                                onUpdate(member.userId, {role: 'MEMBER'}),
                              )
                            }>
                            Remove admin
                          </button>
                        ) : null}
                        {actions.makeOwner ? (
                          <button
                            type="button"
                            className="btn ghost tiny"
                            disabled={rowBusy}
                            onClick={() => {
                              if (
                                !window.confirm(
                                  `Make ${memberLabel(member)} the new room owner? You will become an admin.`,
                                )
                              ) {
                                return;
                              }
                              void run(member.userId, () => onTransferOwnership(member.userId));
                            }}>
                            Make owner
                          </button>
                        ) : null}
                        {actions.remove ? (
                          <button
                            type="button"
                            className="btn ghost tiny danger"
                            disabled={rowBusy}
                            onClick={() => {
                              if (
                                !window.confirm(
                                  `Remove ${memberLabel(member)} from this room?`,
                                )
                              ) {
                                return;
                              }
                              void run(member.userId, () => onRemove(member.userId));
                            }}>
                            Remove
                          </button>
                        ) : null}
                      </>
                    ) : (
                      <p className="chat-members-row-hint muted tiny">{reason}</p>
                    )}
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
        <div className="confirm-dialog-actions">
          <button type="button" className="btn secondary" onClick={onClose} disabled={busy}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
