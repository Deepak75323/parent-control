import {type ReactNode} from 'react';
import {Avatar} from '../Avatar';
import type {ChatCallParticipant, ChatMemberItem, ChatRoomSummary} from '../../types/api';

type Props = {
  open: boolean;
  width: number;
  isMobile?: boolean;
  isResizable?: boolean;
  onClose: () => void;
  /** Kept for callers; room profile UI removed — help text is shown when idle. */
  room?: ChatRoomSummary | null;
  members?: ChatMemberItem[];
  myUserId?: string;
  canManageMembers?: boolean;
  busy?: boolean;
  onInvite?: () => void;
  onManageMembers?: () => void;
  /** When set, show live call participants. */
  callParticipants?: ChatCallParticipant[] | null;
  callChannelName?: string | null;
  sideChat?: ReactNode;
};

export function ParentChatInfoPanel({
  open,
  width,
  isMobile = false,
  isResizable = false,
  onClose,
  myUserId,
  busy = false,
  onInvite,
  callParticipants = null,
  callChannelName = null,
  sideChat = null,
}: Props) {
  if (!open) {
    return null;
  }

  const inCall = Boolean(callParticipants && callParticipants.length > 0);

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
      aria-label={inCall ? 'Call details' : 'Parent chat help'}
      aria-hidden={!open}>
      <div className="chat-info-panel-inner">
        <div className="chat-info-panel-head">
          <span className="chat-info-panel-title">
            {inCall ? 'Participants' : 'Parent chat'}
          </span>
          <button
            type="button"
            className="chat-info-panel-close"
            aria-label="Close parent chat panel"
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
          <div className="chat-side-help">
            <p>
              Create a room, then invite another parent by <strong>@handle</strong> or{' '}
              <strong>email</strong>.
            </p>
            <ol>
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
