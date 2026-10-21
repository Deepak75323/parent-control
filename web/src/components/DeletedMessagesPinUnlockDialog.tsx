import {type FormEvent, useEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import {Link} from 'react-router-dom';
import {deletedMessagesPinApi} from '../api/services';
import {isDeletedMessagesPinDigits} from '../lib/deletedMessagesPin';

type Props = {
  open: boolean;
  onCancel: () => void;
  onUnlocked: () => void;
};

export function DeletedMessagesPinUnlockDialog({open, onCancel, onUnlocked}: Props) {
  const [pin, setPin] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [configured, setConfigured] = useState<boolean | null>(null);
  const onUnlockedRef = useRef(onUnlocked);
  onUnlockedRef.current = onUnlocked;

  useEffect(() => {
    if (!open) {
      return;
    }
    setPin('');
    setError(null);
    setBusy(true);
    void deletedMessagesPinApi
      .status()
      .then(({data}) => {
        const status = data.data;
        setConfigured(Boolean(status?.pinConfigured));
        if (status?.unlocked) {
          onUnlockedRef.current();
        }
      })
      .catch(() => {
        setError('Could not check deleted messages PIN');
        setConfigured(false);
      })
      .finally(() => setBusy(false));
  }, [open]);

  if (!open) {
    return null;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!isDeletedMessagesPinDigits(pin)) {
      setError('Enter your 4–8 digit PIN');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const {data} = await deletedMessagesPinApi.verify(pin.trim());
      if (!data.data?.verified) {
        setError('Incorrect PIN');
        return;
      }
      onUnlocked();
    } catch {
      setError('Could not verify PIN');
    } finally {
      setBusy(false);
    }
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
      <div
        className="confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="deleted-pin-title">
        <h2 id="deleted-pin-title" className="confirm-dialog-title">
          Review deleted notes
        </h2>
        {configured === false ? (
          <>
            <p className="confirm-dialog-message">
              Set a deleted messages PIN in Settings before you can review deleted notes.
            </p>
            {error ? <p className="error">{error}</p> : null}
            <div className="confirm-dialog-actions">
              <button type="button" className="btn ghost" onClick={onCancel}>
                Cancel
              </button>
              <Link className="btn primary" to="/settings">
                Open Settings
              </Link>
            </div>
          </>
        ) : (
          <form onSubmit={event => void onSubmit(event)}>
            <p className="confirm-dialog-message">Enter your deleted messages PIN to continue.</p>
            <label className="form-row">
              PIN
              <input
                type="password"
                inputMode="numeric"
                autoComplete="off"
                value={pin}
                maxLength={8}
                disabled={busy || configured == null}
                autoFocus
                onChange={event => setPin(event.target.value.replace(/\D/g, '').slice(0, 8))}
              />
            </label>
            {error ? <p className="error">{error}</p> : null}
            <div className="confirm-dialog-actions">
              <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>
                Cancel
              </button>
              <button type="submit" className="btn primary" disabled={busy || configured == null}>
                Unlock
              </button>
            </div>
          </form>
        )}
      </div>
    </div>,
    document.body,
  );
}
