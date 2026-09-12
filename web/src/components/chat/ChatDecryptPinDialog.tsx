import {type FormEvent, useEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import {isStrongChatDecryptCode} from '../../lib/chatDecryptCode';
import {lockBodyScroll} from '../../lib/scrollLock';

export type ChatDecryptDialogMode = 'create' | 'unlock' | 'reset';

type ChatDecryptPinDialogProps = {
  open: boolean;
  mode: ChatDecryptDialogMode;
  busy?: boolean;
  error?: string | null;
  onSubmit: (pin: string) => void;
  onReset: (newPin: string) => void;
  onForgot: () => void;
  onBackToUnlock: () => void;
  onCancel: () => void;
};

export function ChatDecryptPinDialog({
  open,
  mode,
  busy = false,
  error = null,
  onSubmit,
  onReset,
  onForgot,
  onBackToUnlock,
  onCancel,
}: ChatDecryptPinDialogProps) {
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [wipeAck, setWipeAck] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const setupMode = mode === 'create' || mode === 'reset';

  useEffect(() => {
    if (!open) {
      setPin('');
      setConfirmPin('');
      setWipeAck(false);
      return;
    }
    setPin('');
    setConfirmPin('');
    setWipeAck(false);
    return lockBodyScroll();
  }, [open, mode]);

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

  const pinValid = isStrongChatDecryptCode(pin);
  const confirmed = pinValid && pin === confirmPin;
  const canSubmit =
    mode === 'unlock'
      ? pinValid && !busy
      : mode === 'create'
        ? confirmed && !busy
        : confirmed && wipeAck && !busy;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }
    if (mode === 'reset') {
      onReset(pin.trim());
      return;
    }
    onSubmit(pin.trim());
  }

  const title =
    mode === 'create'
      ? 'Create decrypt code'
      : mode === 'reset'
        ? 'Reset decrypt code'
        : 'Decrypt chat';

  const message =
    mode === 'create'
      ? 'Choose a 6-digit decrypt code. Save it privately — not in .env and not in this chat. It cannot be recovered later.'
      : mode === 'reset'
        ? 'Choose a new 6-digit code. This permanently deletes every message in this room for everyone.'
        : 'Enter your 6-digit decrypt code. Decrypt stays on only while this room is open.';

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
          {title}
        </h2>
        <p className="confirm-dialog-message">{message}</p>
        <label className="chat-decrypt-pin-label" htmlFor="chat-decrypt-pin-input">
          {mode === 'reset' ? 'New 6-digit code' : '6-digit decrypt code'}
          <input
            ref={inputRef}
            id="chat-decrypt-pin-input"
            className="chat-decrypt-pin-input"
            type="password"
            inputMode="numeric"
            pattern="\d{6}"
            autoComplete={setupMode ? 'new-password' : 'current-password'}
            maxLength={6}
            value={pin}
            disabled={busy}
            onChange={event => setPin(event.target.value.replace(/\D/g, '').slice(0, 6))}
          />
        </label>
        {setupMode ? (
          <>
            <label className="chat-decrypt-pin-label" htmlFor="chat-decrypt-pin-confirm">
              Confirm 6-digit code
              <input
                id="chat-decrypt-pin-confirm"
                className="chat-decrypt-pin-input"
                type="password"
                inputMode="numeric"
                pattern="\d{6}"
                autoComplete="new-password"
                maxLength={6}
                value={confirmPin}
                disabled={busy}
                onChange={event =>
                  setConfirmPin(event.target.value.replace(/\D/g, '').slice(0, 6))
                }
              />
            </label>
            {confirmPin.length > 0 && pin !== confirmPin ? (
              <p className="chat-decrypt-pin-error">Codes do not match</p>
            ) : null}
          </>
        ) : null}
        {mode === 'reset' ? (
          <label className="chat-decrypt-wipe-ack">
            <input
              type="checkbox"
              checked={wipeAck}
              disabled={busy}
              onChange={event => setWipeAck(event.target.checked)}
            />
            I understand this deletes all messages in this room for everyone
          </label>
        ) : null}
        {error ? <p className="chat-decrypt-pin-error">{error}</p> : null}
        {mode === 'unlock' ? (
          <button
            type="button"
            className="chat-decrypt-forgot-link"
            disabled={busy}
            onClick={onForgot}>
            Forgot PIN?
          </button>
        ) : null}
        {mode === 'reset' ? (
          <button
            type="button"
            className="chat-decrypt-forgot-link"
            disabled={busy}
            onClick={onBackToUnlock}>
            I remember my PIN
          </button>
        ) : null}
        <div className="confirm-dialog-actions">
          <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={!canSubmit}>
            {mode === 'create' ? 'Create code' : mode === 'reset' ? 'Wipe & reset' : 'Unlock'}
          </button>
        </div>
      </form>
    </div>,
    document.body,
  );
}
