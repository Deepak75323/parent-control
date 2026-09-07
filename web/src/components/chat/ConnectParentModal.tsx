import {type FormEvent, useEffect, useId, useRef, useState} from 'react';

type Props = {
  open: boolean;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onConnect: (identifier: string, roomName: string) => Promise<void>;
};

/**
 * Connect Parent — creates a room and invites by @handle or email (existing chat APIs).
 */
export function ConnectParentModal({open, busy, error, onClose, onConnect}: Props) {
  const titleId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [identifier, setIdentifier] = useState('');
  const [roomName, setRoomName] = useState('');

  useEffect(() => {
    if (!open) {
      return;
    }
    setIdentifier('');
    setRoomName('');
    const timer = window.setTimeout(() => inputRef.current?.focus(), 50);
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) {
        onClose();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, busy, onClose]);

  if (!open) {
    return null;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const id = identifier.trim();
    if (!id) {
      return;
    }
    const name =
      roomName.trim() ||
      `Chat with ${id.replace(/^@/, '').split('@')[0] || 'parent'}`.slice(0, 64);
    await onConnect(id, name);
  }

  return (
    <div className="confirm-dialog-overlay" role="presentation" onClick={() => !busy && onClose()}>
      <div
        className="confirm-dialog chat-connect-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={event => event.stopPropagation()}>
        <div className="chat-connect-modal-head">
          <h2 id={titleId} className="confirm-dialog-title">
            Connect parent
          </h2>
          <button
            type="button"
            className="chat-icon-btn"
            aria-label="Close"
            disabled={busy}
            onClick={onClose}>
            ×
          </button>
        </div>
        <p className="muted tiny chat-connect-modal-help">
          Search by <strong>@family handle</strong> or <strong>email</strong>. We create a private
          room and send them an invite.
        </p>
        <form className="chat-connect-form" onSubmit={event => void handleSubmit(event)}>
          <label className="chat-connect-label" htmlFor="connect-parent-id">
            Parent identifier
          </label>
          <input
            ref={inputRef}
            id="connect-parent-id"
            type="text"
            autoComplete="off"
            placeholder="@handle or email"
            value={identifier}
            disabled={busy}
            onChange={event => setIdentifier(event.target.value)}
          />
          <label className="chat-connect-label" htmlFor="connect-room-name">
            Room name <span className="muted">(optional)</span>
          </label>
          <input
            id="connect-room-name"
            type="text"
            maxLength={64}
            placeholder="e.g. School pickup"
            value={roomName}
            disabled={busy}
            onChange={event => setRoomName(event.target.value)}
          />
          {error ? (
            <p className="chat-connect-error" role="alert">
              {error}
            </p>
          ) : null}
          <div className="confirm-dialog-actions">
            <button type="button" className="btn" disabled={busy} onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={busy || !identifier.trim()}>
              {busy ? 'Connecting…' : 'Connect'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
