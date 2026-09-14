import {useMemo, useState, type ReactNode} from 'react';
import {Link} from 'react-router-dom';
import {Avatar} from '../Avatar';
import {
  buildMemberRoster,
  type MemberPresenceView,
  type MemberRosterEntry,
} from '../../lib/chatMemberRoster';
import {shouldShowMembersRoster} from '../../lib/membersPanelVisibility';
import {formatLastSeenClockLabel} from '../../lib/parentPresence';
import type {ChatCallParticipant, ChatMemberItem, ChatRoomSummary} from '../../types/api';
import {isNotesServerName, NotesServerIcon, ServerGlyph} from './NotesServerIcon';

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
  /** Owner-only: share/hide Members roster for other parents. */
  onMembersPanelShareChange?: (shared: boolean) => void;
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
  onMembersPanelShareChange,
  callParticipants = null,
  callChannelName = null,
  sideChat = null,
}: Props) {
  const [query, setQuery] = useState('');
  const [nowMs] = useState(() => Date.now());

  const isOwner = Boolean(room?.owner);
  const membersPanelShared = Boolean(room?.membersPanelShared);
  const showMembersRoster = shouldShowMembersRoster({
    isOwner,
    membersPanelShared,
  });

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
  const roomMemberLabel = `${room?.memberCount ?? memberCount} member${
    (room?.memberCount ?? memberCount) === 1 ? '' : 's'
  }`;
  const roomKindLabel = room?.directMessage ? 'Direct message' : 'Private room';

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
      aria-label={inCall ? 'Call details' : showMembersRoster ? 'Members' : 'Parent chat help'}
      aria-hidden={!open}>
      <div className="chat-info-panel-inner">
        <div className="chat-info-panel-head">
          <span className="chat-info-panel-title">
            {inCall ? 'Participants' : showMembersRoster ? `Members (${memberCount})` : 'Parent chat'}
          </span>
          <button
            type="button"
            className="chat-info-panel-close"
            aria-label={showMembersRoster ? 'Close members panel' : 'Close parent chat panel'}
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
        ) : showMembersRoster ? (
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
                  title="Invite members"
                  aria-label="Invite members"
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
              <div className="chat-members-room-card">
                <div className="chat-members-room-card-main">
                  {isNotesServerName(room.name) ? (
                    <NotesServerIcon size={28} />
                  ) : (
                    <ServerGlyph name={room.name} size="md" />
                  )}
                  <div className="chat-members-room-card-copy">
                    <span className="chat-members-room-card-name">{room.name}</span>
                    <span className="chat-members-room-card-meta">
                      {roomMemberLabel} · {roomKindLabel}
                    </span>
                  </div>
                </div>
                {canManageMembers && onManageMembers ? (
                  <button
                    type="button"
                    className="chat-members-manage-btn"
                    disabled={busy}
                    onClick={onManageMembers}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                      <circle cx="12" cy="12" r="3" />
                      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                    </svg>
                    Manage
                  </button>
                ) : null}
              </div>
            ) : null}

            {isOwner && onMembersPanelShareChange ? (
              <div className="chat-members-share-row">
                <div className="chat-members-share-copy">
                  <span className="chat-members-share-title">Show to other parents</span>
                  <span className="chat-members-share-meta">
                    {membersPanelShared
                      ? 'Other parents see this Members panel'
                      : 'Other parents see the older Parent chat help'}
                  </span>
                </div>
                <button
                  type="button"
                  className={`chat-members-share-toggle${membersPanelShared ? ' on' : ''}`}
                  role="switch"
                  aria-checked={membersPanelShared}
                  disabled={busy}
                  onClick={() => onMembersPanelShareChange(!membersPanelShared)}>
                  <span className="chat-members-share-knob" />
                </button>
              </div>
            ) : null}

            {filteredMembers.length === 0 ? (
              <p className="muted tiny chat-members-empty">No members match your search.</p>
            ) : (
              <>
                <MemberSection title="Online" count={roster.online.length} online sortHint="Online first">
                  {roster.online.map(entry => (
                    <MemberRow
                      key={entry.member.userId}
                      entry={entry}
                      name={memberDisplayName(entry.member, myUserId)}
                      online
                      nowMs={nowMs}
                      onManage={onManageMembers}
                      busy={busy}
                    />
                  ))}
                </MemberSection>
                <MemberSection title="Offline" count={roster.offline.length} sortHint="Last seen">
                  {roster.offline.map(entry => (
                    <MemberRow
                      key={entry.member.userId}
                      entry={entry}
                      name={memberDisplayName(entry.member, myUserId)}
                      online={false}
                      nowMs={nowMs}
                      onManage={onManageMembers}
                      busy={busy}
                    />
                  ))}
                </MemberSection>
              </>
            )}

            <div className="chat-members-privacy-note">
              <span className="chat-members-privacy-icon" aria-hidden>
                i
              </span>
              <p>
                Status shows only when Share presence is On in Settings. Otherwise parents appear
                Offline.{' '}
                <Link to="/settings" className="chat-members-privacy-link">
                  Learn more
                </Link>
              </p>
            </div>
          </div>
        ) : (
          <div className="chat-side-help">
            <p>
              Create a room, then invite another parent by <strong>@handle</strong> or{' '}
              <strong>email</strong>.
            </p>
            <ol className="chat-side-help-steps">
              <li>Create a room on the left</li>
              <li>Invite a parent</li>
              <li>They accept the invite</li>
              <li>Chat in real time</li>
            </ol>
            <p className="muted tiny">Room owners can delete a room for everyone.</p>
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
          <span className="chat-members-section-label">{title}</span>
          <span className="chat-members-section-count">{count}</span>
        </h3>
        {sortHint ? (
          <span className="chat-members-section-hint">
            {sortHint}
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden>
              <path d="M6 9l6 6 6-6" />
            </svg>
          </span>
        ) : null}
      </div>
      <ul className="chat-members-roster-list">{children}</ul>
    </section>
  );
}

function MemberRow({
  entry,
  name,
  online,
  nowMs,
  onManage,
  busy,
}: {
  entry: MemberRosterEntry;
  name: string;
  online: boolean;
  nowMs: number;
  onManage?: () => void;
  busy?: boolean;
}) {
  const isOwner = entry.member.role === 'OWNER';
  const clockLabel =
    !online && !entry.presence.hidden
      ? formatLastSeenClockLabel(entry.presence.lastSeenAt, nowMs)
      : null;

  return (
    <li className={`chat-members-roster-row${online ? ' online' : ' offline'}`}>
      <div className="chat-members-roster-avatar">
        <Avatar name={name} seed={entry.member.userId} size="lg" />
        <span
          className={`chat-members-presence-dot${online ? ' online' : ''}`}
          aria-hidden
        />
      </div>
      <div className="chat-members-roster-copy">
        <div className="chat-members-roster-name-row">
          <span className="chat-members-roster-name">{name}</span>
          {isOwner ? (
            <span className="chat-members-roster-role" title="Owner">
              <span aria-hidden>♛</span>
              Owner
            </span>
          ) : null}
        </div>
        {online ? (
          <div className="chat-members-roster-meta">
            <span className="chat-members-roster-status online">
              <span className="chat-members-roster-status-dot" aria-hidden />
              {entry.statusLabel}
            </span>
            <span className="chat-members-roster-active">Active now</span>
          </div>
        ) : (
          <div className="chat-members-roster-meta chat-members-roster-meta--offline">
            <span className="chat-members-roster-status">{entry.statusLabel}</span>
            {clockLabel ? (
              <span className="chat-members-roster-clock">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                  <circle cx="12" cy="12" r="9" />
                  <path d="M12 7v5l3 2" />
                </svg>
                {clockLabel}
              </span>
            ) : null}
          </div>
        )}
      </div>
      {onManage ? (
        <button
          type="button"
          className="chat-members-roster-menu"
          disabled={busy}
          aria-label={`Manage ${name}`}
          title="Manage"
          onClick={onManage}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
            <circle cx="12" cy="5" r="1.6" />
            <circle cx="12" cy="12" r="1.6" />
            <circle cx="12" cy="19" r="1.6" />
          </svg>
        </button>
      ) : null}
    </li>
  );
}
