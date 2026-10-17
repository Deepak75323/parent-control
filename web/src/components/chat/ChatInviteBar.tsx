import {type FormEvent} from 'react';

type Props = {
  identifier: string;
  busy?: boolean;
  visible?: boolean;
  onIdentifierChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
};

export function ChatInviteBar({
  identifier,
  busy = false,
  visible = true,
  onIdentifierChange,
  onSubmit,
}: Props) {
  if (!visible) {
    return null;
  }

  return (
    <form className="chat-invite-bar" onSubmit={onSubmit}>
      <span className="chat-invite-bar-icon" aria-hidden>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M19 8v6M22 11h-6" />
        </svg>
      </span>
      <input
        type="text"
        placeholder="Invite parent — @handle or email"
        value={identifier}
        disabled={busy}
        onChange={event => onIdentifierChange(event.target.value)}
      />
      <button type="submit" className="btn primary sm" disabled={busy || !identifier.trim()}>
        Invite
      </button>
    </form>
  );
}
