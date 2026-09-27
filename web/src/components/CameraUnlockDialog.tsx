import {type FormEvent, useEffect, useRef, useState} from 'react';
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
import {stopLivePipCue} from '../livestream/pipAudible';

type Props = {
  open: boolean;
  userId: string;
  /** Start already completed WebAuthn in the same tap — skip to decrypt code. */
  biometricAlreadyPassed?: boolean;
  onUnlocked: () => void;
  onCancel: () => void;
};

type Step = 'loading' | 'fingerprint' | 'password';

/**
 * Gate for CAMERA live view:
 * - If a fingerprint is registered: biometric first, then decrypt code (both required).
 * - If not: decrypt code only.
 * Prefer running fingerprint from the Start button (same tap); this dialog auto-starts
 * fingerprint when opened without biometricAlreadyPassed.
 */
export function CameraUnlockDialog({
  open,
  userId,
  biometricAlreadyPassed = false,
  onUnlocked,
  onCancel,
}: Props) {
  const [step, setStep] = useState<Step>('loading');
  const [requiresFingerprint, setRequiresFingerprint] = useState(false);
  const [biometricPassed, setBiometricPassed] = useState(false);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const autoStartedRef = useRef(false);

  useEffect(() => {
    if (!open) {
      setStep('loading');
      setRequiresFingerprint(false);
      setBiometricPassed(false);
      setPin('');
      setError(null);
      setBusy(false);
      autoStartedRef.current = false;
      return;
    }
    const unlockScroll = lockBodyScroll();
    let cancelled = false;
    void (async () => {
      if (biometricAlreadyPassed) {
        setRequiresFingerprint(true);
        setBiometricPassed(true);
        setStep('password');
        return;
      }
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
  }, [open, userId, biometricAlreadyPassed]);

  useEffect(() => {
    if (!open || step !== 'fingerprint' || biometricPassed || autoStartedRef.current) {
      return;
    }
    autoStartedRef.current = true;
    void tryFingerprint();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once when fingerprint step appears
  }, [open, step, biometricPassed]);

  if (!open || typeof document === 'undefined') {
    return null;
  }

  async function tryFingerprint() {
    setBusy(true);
    setError(null);
    try {
      const ok = await assertParentWebAuthn(userId);
      if (!ok) {
        setError('Fingerprint not verified. Tap Retry.');
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
      autoStartedRef.current = false;
      return;
    }
    if (!/^\d{6}$/.test(pin.trim())) {
      setError('Enter your 6-digit decrypt code');
      return;
    }
    stopLivePipCue();
    setBusy(true);
    setError(null);
    try {
      await chatApi.verifyDecryptPin(pin.trim());
      onUnlocked();
    } catch {
      stopLivePipCue();
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
      ? busy
        ? 'Confirm with fingerprint on your device…'
        : 'Fingerprint prompt should appear. If it did not, tap Retry — then enter your decrypt code.'
      : requiresFingerprint
        ? 'Fingerprint OK. Enter your 6-digit chat decrypt code to open the camera.'
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
        {step === 'loading' ? <p className="muted">Starting unlock…</p> : null}
        {step === 'fingerprint' ? (
          <div className="confirm-dialog-actions" style={{flexDirection: 'column', gap: 8}}>
            {busy ? <p className="muted">Waiting for fingerprint…</p> : null}
            {!busy ? (
              <button
                type="button"
                className="btn primary"
                onClick={() => {
                  autoStartedRef.current = true;
                  void tryFingerprint();
                }}>
                Retry fingerprint
              </button>
            ) : null}
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
                    autoStartedRef.current = false;
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
