import {type FormEvent, useEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import {chatApi} from '../api/services';
import {lockBodyScroll} from '../lib/scrollLock';
import {
  assertParentWebAuthn,
  describeWebAuthnError,
  hasParentWebAuthnCredentials,
  isWebAuthnAvailable,
} from '../lib/webAuthnParentGate';
import {
  cameraUnlockCanEnterDecryptCode,
  cameraUnlockNeedsFingerprintFirst,
} from '../lib/cameraUnlockPolicy';

type Props = {
  open: boolean;
  userId: string;
  onUnlocked: () => void;
  onCancel: () => void;
};

type Step = 'loading' | 'fingerprint' | 'password';

/**
 * Gate for CAMERA live view:
 * - If a fingerprint is registered: biometric first, then decrypt code (both required).
 * - If not: decrypt code only.
 */
export function CameraUnlockDialog({open, userId, onUnlocked, onCancel}: Props) {
  const [step, setStep] = useState<Step>('loading');
  const [requiresFingerprint, setRequiresFingerprint] = useState(false);
  const [biometricPassed, setBiometricPassed] = useState(false);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setStep('loading');
      setRequiresFingerprint(false);
      setBiometricPassed(false);
      setPin('');
      setError(null);
      setBusy(false);
      return;
    }
    const unlockScroll = lockBodyScroll();
    let cancelled = false;
    void (async () => {
      const hasFp =
        isWebAuthnAvailable() && (await hasParentWebAuthnCredentials(userId));
      if (cancelled) {
        return;
      }
      const requires = cameraUnlockNeedsFingerprintFirst(hasFp);
      setRequiresFingerprint(requires);
      setBiometricPassed(!requires);
      setStep(requires ? 'fingerprint' : 'password');
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
      const ok = await assertParentWebAuthn(userId);
      if (!ok) {
        setError('Fingerprint not verified. Try again.');
        return;
      }
      setBiometricPassed(true);
      setStep('password');
    } catch (err) {
      setError(describeWebAuthnError(err));
    } finally {
      setBusy(false);
    }
  }

  async function tryPassword(event: FormEvent) {
    event.preventDefault();
    if (
      !cameraUnlockCanEnterDecryptCode({
        hasRegisteredFingerprint: requiresFingerprint,
        biometricPassed,
      })
    ) {
      setError('Complete fingerprint first');
      setStep('fingerprint');
      return;
    }
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

  const title =
    step === 'password' && biometricPassed && requiresFingerprint
      ? 'Enter decrypt code'
      : 'Unlock camera';

  const message =
    step === 'fingerprint'
      ? 'Step 1 of 2: confirm with fingerprint. You will still enter your decrypt code next — so another fingerprint on this phone is not enough alone.'
      : requiresFingerprint
        ? 'Step 2 of 2: enter your 6-digit chat decrypt code to open the camera.'
        : 'Enter your 6-digit chat decrypt code to open the camera. (No fingerprint is registered on this browser.)';

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
          {title}
        </h2>
        <p className="confirm-dialog-message">{message}</p>
        {step === 'loading' ? <p className="muted">Checking fingerprint…</p> : null}
        {step === 'fingerprint' ? (
          <div className="confirm-dialog-actions" style={{flexDirection: 'column', gap: 8}}>
            <button
              type="button"
              className="btn primary"
              disabled={busy}
              onClick={() => void tryFingerprint()}>
              {busy ? 'Waiting…' : 'Use fingerprint'}
            </button>
            <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>
              Cancel
            </button>
            {error ? <p className="error">{error}</p> : null}
          </div>
        ) : null}
        {step === 'password' ? (
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
              {requiresFingerprint ? (
                <button
                  type="button"
                  className="btn ghost"
                  disabled={busy}
                  onClick={() => {
                    setBiometricPassed(false);
                    setPin('');
                    setError(null);
                    setStep('fingerprint');
                  }}>
                  Back
                </button>
              ) : (
                <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>
                  Cancel
                </button>
              )}
              <button type="submit" className="btn primary" disabled={busy}>
                Unlock camera
              </button>
            </div>
          </form>
        ) : null}
      </div>
    </div>,
    document.body,
  );
}
