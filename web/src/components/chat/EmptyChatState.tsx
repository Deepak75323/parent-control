import {type FormEvent} from 'react';
import {Avatar} from '../Avatar';

type Props = {
  title: string;
  subtitle: string;
  avatarName: string;
  avatarSeed: string;
  showInvite?: boolean;
  inviteIdentifier?: string;
  busy?: boolean;
  onInviteIdentifierChange?: (value: string) => void;
  onInvite?: (event: FormEvent) => void;
};

export function EmptyChatState({
  title,
  subtitle,
  avatarName,
  avatarSeed,
  showInvite = false,
  inviteIdentifier = '',
  busy = false,
  onInviteIdentifierChange,
  onInvite,
}: Props) {
  return (
    <div className="chat-messages-empty-state">
      <Avatar name={avatarName} seed={avatarSeed} size="lg" />
      <h3>{title}</h3>
      <p className="muted">{subtitle}</p>
      {showInvite && onInvite && onInviteIdentifierChange ? (
        <form className="chat-empty-invite" onSubmit={onInvite}>
          <span className="chat-empty-invite-icon" aria-hidden>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M19 8v6M22 11h-6" />
            </svg>
          </span>
          <input
            type="text"
            placeholder="Invite parent — @handle or email"
            value={inviteIdentifier}
            disabled={busy}
            onChange={event => onInviteIdentifierChange(event.target.value)}
          />
          <button type="submit" className="btn primary sm" disabled={busy || !inviteIdentifier.trim()}>
            Invite
          </button>
        </form>
      ) : null}
    </div>
  );
}
