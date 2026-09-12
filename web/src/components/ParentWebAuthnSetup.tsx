import {useEffect, useState} from 'react';
import {useAuthStore} from '../auth/authStore';
import {userIdFromAccessToken} from '../lib/jwtSubject';
import {
  describeWebAuthnError,
  hasParentWebAuthnCredentials,
  isPlatformAuthenticatorAvailable,
  isWebAuthnAvailable,
  listParentWebAuthnCredentials,
  registerParentWebAuthn,
  removeParentWebAuthnCredential,
  type ParentWebAuthnCredential,
} from '../lib/webAuthnParentGate';

/**
 * On portal open after login: if no fingerprint is registered and WebAuthn is
 * available, prompt once to register. Credentials persist in IndexedDB.
 */
export function ParentWebAuthnSetup() {
  const accessToken = useAuthStore(s => s.accessToken);
  const profile = useAuthStore(s => s.profile);
  const userId = userIdFromAccessToken(accessToken) ?? profile?.userId;
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [credentials, setCredentials] = useState<ParentWebAuthnCredential[]>([]);
  const [name, setName] = useState('Primary fingerprint');

  useEffect(() => {
    if (!accessToken || !userId || !isWebAuthnAvailable()) {
      return;
    }
    let cancelled = false;
    void (async () => {
      const [list, platformReady] = await Promise.all([
        listParentWebAuthnCredentials(userId),
        isPlatformAuthenticatorAvailable(),
      ]);
      if (cancelled) {
        return;
      }
      setCredentials(list);
      // Without a screen lock / passkey provider the prompt can only fail — stay out of the way.
      if (list.length === 0 && platformReady) {
        setOpen(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [accessToken, userId]);

  if (!open || !userId) {
    return null;
  }

  async function handleRegister() {
    if (!userId) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const entry = await registerParentWebAuthn(
        userId,
        profile?.email ?? profile?.firstName ?? 'Parent',
        name,
      );
      setCredentials(prev => [...prev.filter(c => c.credentialId !== entry.credentialId), entry]);
      setOpen(false);
    } catch (err) {
      setError(describeWebAuthnError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="confirm-dialog-overlay" role="presentation">
      <div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="webauthn-setup-title">
        <h2 id="webauthn-setup-title" className="confirm-dialog-title">
          Set fingerprint for camera
        </h2>
        <p className="confirm-dialog-message">
          Register a device fingerprint (Touch ID, Face ID, or Windows Hello). It is required to open
          the child camera on the web. You can add more later; if biometrics fail, use your decrypt
          code instead.
        </p>
        <label className="form-row">
          Fingerprint name
          <input
            type="text"
            value={name}
            maxLength={40}
            disabled={busy}
            onChange={e => setName(e.target.value)}
          />
        </label>
        {credentials.length > 0 ? (
          <ul className="muted tiny">
            {credentials.map(c => (
              <li key={c.credentialId}>{c.name}</li>
            ))}
          </ul>
        ) : null}
        {error ? <p className="error">{error}</p> : null}
        <div className="confirm-dialog-actions">
          <button type="button" className="btn ghost" disabled={busy} onClick={() => setOpen(false)}>
            {error ? 'Use decrypt code' : 'Later'}
          </button>
          <button type="button" className="btn primary" disabled={busy} onClick={() => void handleRegister()}>
            {busy ? 'Waiting…' : error ? 'Try again' : 'Register fingerprint'}
          </button>
        </div>
      </div>
    </div>
  );
}

export async function ensureParentHasWebAuthn(userId: string): Promise<boolean> {
  return hasParentWebAuthnCredentials(userId);
}

/** Settings: list + add fingerprints (collection). */
export function FingerprintSettingsCard() {
  const accessToken = useAuthStore(s => s.accessToken);
  const profile = useAuthStore(s => s.profile);
  const userId = userIdFromAccessToken(accessToken) ?? profile?.userId;
  const [credentials, setCredentials] = useState<ParentWebAuthnCredential[]>([]);
  const [name, setName] = useState('Fingerprint');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!userId) {
      return;
    }
    void listParentWebAuthnCredentials(userId).then(setCredentials);
  }, [userId]);

  if (!isWebAuthnAvailable()) {
    return (
      <div className="card">
        <h2>Camera fingerprints</h2>
        <p className="muted">This browser does not support fingerprint / biometric unlock.</p>
      </div>
    );
  }

  if (!userId) {
    return null;
  }

  async function addFingerprint() {
    setBusy(true);
    setMsg(null);
    try {
      const entry = await registerParentWebAuthn(
        userId!,
        profile?.email ?? profile?.firstName ?? 'Parent',
        name,
      );
      setCredentials(prev => [...prev.filter(c => c.credentialId !== entry.credentialId), entry]);
      setMsg(`Added “${entry.name}”`);
      setName('Fingerprint');
    } catch (err) {
      setMsg(describeWebAuthnError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card">
      <h2>Camera fingerprints</h2>
      <p className="muted">
        Registered biometrics unlock the child camera on this browser. Add more than one if needed.
        If fingerprint fails, use your chat decrypt code.
      </p>
      {credentials.length === 0 ? (
        <p className="muted tiny">None registered yet.</p>
      ) : (
        <ul>
          {credentials.map(c => (
            <li key={c.credentialId}>
              {c.name}{' '}
              <span className="muted tiny">
                ({new Date(c.createdAt).toLocaleString()})
              </span>{' '}
              <button
                type="button"
                className="btn ghost"
                disabled={busy}
                onClick={() => {
                  void (async () => {
                    await removeParentWebAuthnCredential(userId, c.credentialId);
                    setCredentials(prev => prev.filter(x => x.credentialId !== c.credentialId));
                  })();
                }}>
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="form-row">
        <label>
          Name
          <input
            type="text"
            value={name}
            maxLength={40}
            disabled={busy}
            onChange={e => setName(e.target.value)}
          />
        </label>
        <button
          type="button"
          className="btn primary"
          disabled={busy}
          onClick={() => void addFingerprint()}>
          {busy ? 'Waiting…' : 'Add fingerprint'}
        </button>
      </div>
      {msg ? <p className="muted">{msg}</p> : null}
    </div>
  );
}
