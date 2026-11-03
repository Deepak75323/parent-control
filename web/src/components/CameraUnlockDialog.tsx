import {type FormEvent, useEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import {cameraPasswordApi} from '../api/services';
import {lockBodyScroll} from '../lib/scrollLock';
import {
  assertParentWebAuthn,
  describeWebAuthnError,
  hasParentWebAuthnCredentials,
  isWebAuthnAvailable,
} from '../lib/webAuthnParentGate';
import {
  cameraUnlockCanEnterPassword,
  cameraUnlockNeedsFingerprintFirst,
} from '../lib/cameraUnlockPolicy';
import {isCameraPasswordDigits} from '../lib/cameraPassword';
import {stopLivePipCue} from '../livestream/pipAudible';

type Props = {
  open: boolean;
  userId: string;
  /** Start already completed WebAuthn in the same tap — skip to camera password. */
  biometricAlreadyPassed?: boolean;
  onUnlocked: () => void;
  onCancel: () => void;
};

type Step = 'loading' | 'fingerprint' | 'password';

/**
 * Gate for CAMERA live view:
 * - If a fingerprint is registered: biometric first, then camera password (both required).
 * - If not: camera password only.
 * The camera password is a separate credential from the chat decrypt code and is
 * set/changed only in Settings (current + new + confirm).
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
  const [passwordConfigured, setPasswordConfigured] = useState(true);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const autoStartedRef = useRef(false);

  useEffect(() => {
    if (!open) {
      setStep('loading');
      setRequiresFingerprint(false);
      setBiometricPassed(false);
      setPasswordConfigured(true);
      setPin('');
      setError(null);
      setBusy(false);
      autoStartedRef.current = false;
      return;
    }
    const unlockScroll = lockBodyScroll();
    let cancelled = false;
    void (async () => {
      try {
        const {data} = await cameraPasswordApi.status();
        if (!cancelled) {
          setPasswordConfigured(Boolean(data.data?.pinConfigured));
        }
      } catch {
        if (!cancelled) {
          setPasswordConfigured(false);
        }
      }
      if (cancelled) {
        return;
      }
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
      !cameraUnlockCanEnterPassword({
        hasRegisteredFingerprint: requiresFingerprint,
        biometricPassed,
      })
    ) {
      setError('Complete fingerprint first');
      setStep('fingerprint');
      autoStartedRef.current = false;
      return;
    }
    if (!isCameraPasswordDigits(pin)) {
      setError('Enter your 4–8 digit camera password');
      return;
    }
    stopLivePipCue();
    setBusy(true);
    setError(null);
    try {
      const {data} = await cameraPasswordApi.verify(pin.trim());
      if (data.data?.verified) {
        onUnlocked();
        return;
      }
      stopLivePipCue();
      setError('Invalid camera password');
    } catch {
      stopLivePipCue();
      setError('Invalid camera password');
    } finally {
      setBusy(false);
    }
  }

  const needsSetup = step === 'password' && !passwordConfigured;

  const title = needsSetup
    ? 'Set a camera password first'
    : step === 'password' && biometricPassed && requiresFingerprint
      ? 'Enter camera password'
      : 'Unlock camera';

  const message = needsSetup
    ? 'Camera unlock uses its own password, not the chat decrypt code. Create one in Settings → Camera password, then come back.'
    : step === 'fingerprint'
      ? busy
        ? 'Confirm with fingerprint on your device…'
        : 'Fingerprint prompt should appear. If it did not, tap Retry — then enter your camera password.'
      : requiresFingerprint
        ? 'Fingerprint OK. Enter your camera password to open the camera.'
        : 'Enter your camera password to open the camera. (No fingerprint is registered on this browser.)';

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
        {needsSetup ? (
          <div className="confirm-dialog-actions">
            <button type="button" className="btn primary" onClick={onCancel}>
              Got it
            </button>
          </div>
        ) : null}
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
        {step === 'password' && passwordConfigured ? (
          <form onSubmit={event => void tryPassword(event)}>
            <label className="form-row">
              Camera password
              <input
                type="password"
                inputMode="numeric"
                pattern="\d{4,8}"
                value={pin}
                maxLength={8}
                disabled={busy}
                autoFocus
                onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0, 8))}
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
