import {type FormEvent, useEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import {lockBodyScroll} from '../../lib/scrollLock';

type ChatDecryptPinDialogProps = {
  open: boolean;
  busy?: boolean;
  error?: string | null;
  onSubmit: (pin: string) => void;
  onCancel: () => void;
};

export function ChatDecryptPinDialog({
  open,
  busy = false,
  error = null,
  onSubmit,
  onCancel,
}: ChatDecryptPinDialogProps) {
  const [pin, setPin] = useState('');
  const [showForgotHelp, setShowForgotHelp] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) {
      setPin('');
      setShowForgotHelp(false);
      return;
    }
    return lockBodyScroll();
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    inputRef.current?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onCancel();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, onCancel]);

  if (!open || typeof document === 'undefined') {
    return null;
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!/^\d{4}$/.test(pin) || busy) {
      return;
    }
    onSubmit(pin);
  }

  return createPortal(
    <div
      className="confirm-dialog-overlay"
      role="presentation"
      onClick={event => {
        if (event.target === event.currentTarget) {
          onCancel();
        }
      }}>
      <form
        className="confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="chat-decrypt-pin-title"
        onSubmit={handleSubmit}>
        <h2 id="chat-decrypt-pin-title" className="confirm-dialog-title">
          Decrypt chat
        </h2>
        <p className="confirm-dialog-message">
          Enter the 4-digit server PIN. Decrypt stays on only while this room is open.
        </p>
        <label className="chat-decrypt-pin-label" htmlFor="chat-decrypt-pin-input">
          PIN
          <input
            ref={inputRef}
            id="chat-decrypt-pin-input"
            className="chat-decrypt-pin-input"
            type="password"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={4}
            pattern="\d{4}"
            value={pin}
            disabled={busy}
            onChange={event => setPin(event.target.value.replace(/\D/g, '').slice(0, 4))}
          />
        </label>
        {error ? <p className="chat-decrypt-pin-error">{error}</p> : null}
        <button
          type="button"
          className="chat-decrypt-forgot-link"
          disabled={busy}
          onClick={() => setShowForgotHelp(current => !current)}>
          Forgot PIN?
        </button>
        {showForgotHelp ? (
          <div className="chat-decrypt-forgot-help" role="note">
            <p>
              This PIN was created when the server was set up and is only shown once to the person
              who hosts it. The portal cannot reveal or email the current PIN.
            </p>
            <p>
              Ask your server operator to run{' '}
              <code>./scripts/deploy/oracle-reset-chat-decrypt-pin.sh</code> — that generates a{' '}
              <strong>new</strong> PIN (printed once) and reloads the backend. Then use the new PIN
              here.
            </p>
          </div>
        ) : null}
        <div className="confirm-dialog-actions">
          <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>
            Cancel
          </button>
          <button
            type="submit"
            className="btn primary"
            disabled={busy || !/^\d{4}$/.test(pin)}>
            Unlock
          </button>
        </div>
      </form>
    </div>,
    document.body,
  );
}
