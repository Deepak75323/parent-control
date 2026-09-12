import {type FormEvent, useState} from 'react';
import {createPortal} from 'react-dom';
import {chatApi} from '../api/services';
import {lockBodyScroll} from '../lib/scrollLock';
import {
  assertParentWebAuthn,
  hasParentWebAuthnCredentials,
  isWebAuthnAvailable,
} from '../lib/webAuthnParentGate';
import {useEffect} from 'react';

type Props = {
  open: boolean;
  userId: string;
  onUnlocked: () => void;
  onCancel: () => void;
};

/**
 * Gate for starting CAMERA live view: fingerprint first, decrypt code fallback.
 */
export function CameraUnlockDialog({open, userId, onUnlocked, onCancel}: Props) {
  const [mode, setMode] = useState<'choose' | 'password'>('choose');
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setMode('choose');
      setPin('');
      setError(null);
      return;
    }
    const unlockScroll = lockBodyScroll();
    let cancelled = false;
    void (async () => {
      if (!isWebAuthnAvailable() || !(await hasParentWebAuthnCredentials(userId))) {
        if (!cancelled) {
          setMode('password');
        }
      }
    })();
    return () => {
      cancelled = true;
      unlockScroll();
    };
  }, [open, userId]);

  if (!open || typeof document === 'undefined') {
    return null;
  }

  async function tryFingerprint() {
    setBusy(true);
    setError(null);
    try {
      if (!isWebAuthnAvailable()) {
        setMode('password');
        return;
      }
      const ok = await assertParentWebAuthn(userId);
      if (!ok) {
        setError('Fingerprint not verified. Try again or use decrypt code.');
        return;
      }
      onUnlocked();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Fingerprint failed');
      setMode('password');
    } finally {
      setBusy(false);
    }
  }

  async function tryPassword(event: FormEvent) {
    event.preventDefault();
    if (!/^\d{6}$/.test(pin.trim())) {
      setError('Enter your 6-digit decrypt code');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await chatApi.verifyDecryptPin(pin.trim());
      onUnlocked();
    } catch {
      setError('Invalid decrypt code');
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
      <div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="camera-unlock-title">
        <h2 id="camera-unlock-title" className="confirm-dialog-title">
          Unlock camera
        </h2>
        <p className="confirm-dialog-message">
          Confirm with fingerprint to open the child camera. If biometrics are unavailable, use your
          chat decrypt code.
        </p>
        {mode === 'choose' ? (
          <div className="confirm-dialog-actions" style={{flexDirection: 'column', gap: 8}}>
            <button
              type="button"
              className="btn primary"
              disabled={busy}
              onClick={() => void tryFingerprint()}>
              {busy ? 'Waiting…' : 'Use fingerprint'}
            </button>
            <button
              type="button"
              className="btn ghost"
              disabled={busy}
              onClick={() => setMode('password')}>
              Use decrypt code instead
            </button>
            <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>
              Cancel
            </button>
          </div>
        ) : (
          <form onSubmit={event => void tryPassword(event)}>
            <label className="form-row">
              Decrypt code
              <input
                type="password"
                inputMode="numeric"
                pattern="\d{6}"
                value={pin}
                maxLength={6}
                disabled={busy}
                autoFocus
                onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))}
              />
            </label>
            {error ? <p className="error">{error}</p> : null}
            <div className="confirm-dialog-actions">
              <button type="button" className="btn ghost" disabled={busy} onClick={() => setMode('choose')}>
                Back
              </button>
              <button type="submit" className="btn primary" disabled={busy}>
                Unlock
              </button>
            </div>
          </form>
        )}
        {mode === 'choose' && error ? <p className="error">{error}</p> : null}
      </div>
    </div>,
    document.body,
  );
}
