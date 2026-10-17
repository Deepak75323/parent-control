import {type FormEvent, useEffect, useId, useRef, useState} from 'react';

type Props = {
  open: boolean;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  /** Send friend request by @handle or email. */
  onConnect: (identifier: string) => Promise<void>;
};

/**
 * Add friend — sends a friend request by @handle or email.
 * After they accept, a DM room is created automatically.
 */
export function ConnectParentModal({open, busy, error, onClose, onConnect}: Props) {
  const titleId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [identifier, setIdentifier] = useState('');

  useEffect(() => {
    if (!open) {
      return;
    }
    setIdentifier('');
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
    await onConnect(id);
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
            Add friend
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
          Send a friend request by <strong>@family handle</strong> or <strong>email</strong>. When
          they accept, you both get a direct message.
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
          {error ? <p className="chat-connect-error">{error}</p> : null}
          <div className="confirm-dialog-actions">
            <button type="button" className="btn ghost" disabled={busy} onClick={onClose}>
              Cancel
            </button>
            <button
              type="submit"
              className="btn primary"
              disabled={busy || !identifier.trim()}>
              {busy ? 'Sending…' : 'Send friend request'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
