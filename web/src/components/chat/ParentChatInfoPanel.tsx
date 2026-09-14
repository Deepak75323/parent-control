import {useMemo, useState, type ReactNode} from 'react';
import {Avatar} from '../Avatar';
import {
  buildMemberRoster,
  type MemberPresenceView,
} from '../../lib/chatMemberRoster';
import type {ChatCallParticipant, ChatMemberItem, ChatRoomSummary} from '../../types/api';

type Props = {
  open: boolean;
  width: number;
  isMobile?: boolean;
  isResizable?: boolean;
  onClose: () => void;
  room?: ChatRoomSummary | null;
  members?: ChatMemberItem[];
  /** userId → presence (hidden = visibility off → Offline). */
  memberPresenceByUserId?: Record<string, MemberPresenceView>;
  myUserId?: string;
  canManageMembers?: boolean;
  busy?: boolean;
  onInvite?: () => void;
  onManageMembers?: () => void;
  callParticipants?: ChatCallParticipant[] | null;
  callChannelName?: string | null;
  sideChat?: ReactNode;
};

function memberDisplayName(member: ChatMemberItem, myUserId?: string): string {
  if (myUserId && member.userId === myUserId) {
    return 'You';
  }
  return member.nickname?.trim() || member.label?.trim() || 'Parent';
}

export function ParentChatInfoPanel({
  open,
  width,
  isMobile = false,
  isResizable = false,
  onClose,
  room = null,
  members = [],
  memberPresenceByUserId = {},
  myUserId,
  canManageMembers = false,
  busy = false,
  onInvite,
  onManageMembers,
  callParticipants = null,
  callChannelName = null,
  sideChat = null,
}: Props) {
  const [query, setQuery] = useState('');
  const [nowMs] = useState(() => Date.now());

  const filteredMembers = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return members;
    }
    return members.filter(member => {
      const name = `${member.nickname ?? ''} ${member.label}`.toLowerCase();
      return name.includes(q);
    });
  }, [members, query]);

  const roster = useMemo(
    () => buildMemberRoster(filteredMembers, memberPresenceByUserId, nowMs),
    [filteredMembers, memberPresenceByUserId, nowMs],
  );

  if (!open) {
    return null;
  }

  const inCall = Boolean(callParticipants && callParticipants.length > 0);
  const memberCount = members.length;

  return (
    <aside
      className={[
        'chat-info-panel',
        isMobile ? 'chat-info-panel--drawer' : '',
        !open ? 'chat-info-panel--collapsed' : '',
        inCall ? 'chat-info-panel--in-call' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={!isMobile && !isResizable ? {width} : undefined}
      aria-label={inCall ? 'Call details' : 'Members'}
      aria-hidden={!open}>
      <div className="chat-info-panel-inner">
        <div className="chat-info-panel-head">
          <span className="chat-info-panel-title">
            {inCall ? 'Participants' : `Members (${memberCount})`}
          </span>
          <button
            type="button"
            className="chat-info-panel-close"
            aria-label="Close members panel"
            onClick={onClose}>
            ×
          </button>
        </div>

        {inCall ? (
          <div className="chat-info-call-block">
            {callChannelName ? (
              <p className="chat-info-call-channel muted tiny">{callChannelName}</p>
            ) : null}
            <ul className="chat-info-call-list">
              {callParticipants!.map(participant => {
                const isYou = participant.userId === myUserId;
                return (
                  <li key={participant.userId} className="chat-info-call-row">
                    <Avatar
                      name={participant.nickname || participant.label}
                      seed={participant.userId}
                      size="sm"
                    />
                    <span className="chat-info-call-name">
                      {isYou ? 'You' : participant.nickname || participant.label}
                    </span>
                  </li>
                );
              })}
            </ul>
            {onInvite ? (
              <button
                type="button"
                className="btn chat-info-invite-btn"
                disabled={busy}
                onClick={onInvite}>
                + Invite someone
              </button>
            ) : null}
            {sideChat ? <div className="chat-info-side-chat">{sideChat}</div> : null}
          </div>
        ) : (
          <div className="chat-members-roster">
            <div className="chat-members-roster-toolbar">
              <label className="chat-members-search">
                <span className="sr-only">Search members</span>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                  <circle cx="11" cy="11" r="7" />
                  <path d="M20 20l-3-3" />
                </svg>
                <input
                  type="search"
                  placeholder="Search members..."
                  value={query}
                  onChange={event => setQuery(event.target.value)}
                />
              </label>
              {onInvite ? (
                <button
                  type="button"
                  className="chat-members-invite-btn"
                  disabled={busy}
                  title="Invite parents"
                  aria-label="Invite parents"
                  onClick={onInvite}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                    <circle cx="9" cy="7" r="4" />
                    <path d="M19 8v6M22 11h-6" />
                  </svg>
                </button>
              ) : null}
            </div>

            {room ? (
              <p className="chat-members-room-hint muted tiny">
                {room.name}
                {canManageMembers && onManageMembers ? (
                  <>
                    {' · '}
                    <button
                      type="button"
                      className="chat-members-manage-link"
                      disabled={busy}
                      onClick={onManageMembers}>
                      Manage
                    </button>
                  </>
                ) : null}
              </p>
            ) : null}

            {filteredMembers.length === 0 ? (
              <p className="muted tiny chat-members-empty">No members match your search.</p>
            ) : (
              <>
                <MemberSection
                  title="Online"
                  count={roster.online.length}
                  online
                  sortHint="Online first">
                  {roster.online.map(entry => (
                    <MemberRow
                      key={entry.member.userId}
                      name={memberDisplayName(entry.member, myUserId)}
                      seed={entry.member.userId}
                      statusLabel={entry.statusLabel}
                      online
                      role={entry.member.role}
                    />
                  ))}
                </MemberSection>
                <MemberSection
                  title="Offline"
                  count={roster.offline.length}
                  sortHint="Last seen">
                  {roster.offline.map(entry => (
                    <MemberRow
                      key={entry.member.userId}
                      name={memberDisplayName(entry.member, myUserId)}
                      seed={entry.member.userId}
                      statusLabel={entry.statusLabel}
                      online={false}
                      role={entry.member.role}
                    />
                  ))}
                </MemberSection>
              </>
            )}

            <p className="muted tiny chat-members-privacy-note">
              Status shows only when Share presence is On in Settings. Otherwise parents appear
              Offline.
            </p>
          </div>
        )}
      </div>
    </aside>
  );
}

function MemberSection({
  title,
  count,
  online = false,
  sortHint,
  children,
}: {
  title: string;
  count: number;
  online?: boolean;
  sortHint?: string;
  children: ReactNode;
}) {
  if (count === 0) {
    return null;
  }
  return (
    <section
      className={`chat-members-section${online ? ' chat-members-section--online' : ' chat-members-section--offline'}`}>
      <div className="chat-members-section-head">
        <h3 className="chat-members-section-title">
          <span
            className={`chat-members-section-dot${online ? ' online' : ''}`}
            aria-hidden
          />
          <span className="chat-members-section-label">
            {title} — {count}
          </span>
        </h3>
        {sortHint ? <span className="chat-members-section-hint">{sortHint}</span> : null}
      </div>
      <ul className="chat-members-roster-list">{children}</ul>
    </section>
  );
}

function MemberRow({
  name,
  seed,
  statusLabel,
  online,
  role,
}: {
  name: string;
  seed: string;
  statusLabel: string;
  online: boolean;
  role: string;
}) {
  const isOwner = role === 'OWNER';
  return (
    <li className="chat-members-roster-row">
      <div className="chat-members-roster-avatar">
        <Avatar name={name} seed={seed} size="lg" />
        <span
          className={`chat-members-presence-dot${online ? ' online' : ''}`}
          aria-hidden
        />
      </div>
      <div className="chat-members-roster-copy">
        <span className="chat-members-roster-name">{name}</span>
        <div className="chat-members-roster-meta">
          {online ? (
            <span className="chat-members-roster-status online">
              <span className="chat-members-roster-status-dot" aria-hidden />
              {statusLabel}
            </span>
          ) : (
            <span className="chat-members-roster-status">{statusLabel}</span>
          )}
          {isOwner ? (
            <span className="chat-members-roster-role" title="Owner">
              Owner
            </span>
          ) : null}
        </div>
      </div>
    </li>
  );
}
