import {type ReactNode, useCallback, useEffect, useState} from 'react';
import {authApi, portalApi} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {
  shouldShowPortalUnlockForm,
  unlockPortalWebsite,
  validatePortalUnlockInput,
} from '../lib/portalUnlock';

type Props = {children: ReactNode};

/**
 * When the site admin disables the parent portal, render a white screen with an
 * unlock form. Anyone can see the form; only admin/operators can enable it.
 */
export function PortalGate({children}: Props) {
  const storedEmail = useAuthStore(s => s.email);
  const hydrated = useAuthStore(s => s.hydrated);
  const setTokens = useAuthStore(s => s.setTokens);
  const [disabled, setDisabled] = useState(false);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (storedEmail && !email) {
      setEmail(storedEmail);
    }
  }, [storedEmail, email]);

  const refresh = useCallback(async () => {
    try {
      const statusRes = await portalApi.status();
      setDisabled(Boolean(statusRes.data.data?.disabled));
      setError(null);
    } catch {
      // If status is unreachable, do not hard-block the app.
      setDisabled(false);
      setError(null);
    } finally {
      setReady(true);
    }
  }, []);

  useEffect(() => {
    if (!hydrated) {
      return;
    }
    void refresh();
  }, [hydrated, refresh]);

  useEffect(() => {
    if (!disabled) {
      return;
    }
    const id = window.setInterval(() => {
      void refresh();
    }, 5000);
    return () => window.clearInterval(id);
  }, [disabled, refresh]);

  async function unlockSite() {
    const validation = validatePortalUnlockInput(email, password);
    if (validation) {
      setError(validation);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await unlockPortalWebsite({
        email,
        password,
        login: async (loginEmail, loginPassword) => {
          const {data} = await authApi.login(loginEmail, loginPassword);
          if (!data.success || !data.data?.accessToken || !data.data.refreshToken) {
            throw new Error(data.message ?? 'Login failed');
          }
          return {
            accessToken: data.data.accessToken,
            refreshToken: data.data.refreshToken,
            email: data.data.profile?.email ?? loginEmail,
            profile: data.data.profile ?? null,
          };
        },
        setDisabled: async (nextDisabled, pwd) => {
          const res = await portalApi.setDisabled(nextDisabled, pwd);
          return res.data.data ?? {disabled: true};
        },
        setTokens,
      });
      if (!result.ok) {
        setError(result.error);
        return;
      }
      setPassword('');
      setDisabled(false);
    } finally {
      setBusy(false);
    }
  }

  if (!hydrated || !ready) {
    return <div className="portal-white-screen" aria-busy="true" />;
  }

  if (!shouldShowPortalUnlockForm(disabled)) {
    return <>{children}</>;
  }

  return (
    <div className="portal-white-screen" role="status" aria-live="polite">
      <form
        className="portal-white-screen-admin"
        onSubmit={e => {
          e.preventDefault();
          void unlockSite();
        }}>
        <label className="portal-white-screen-password">
          Email
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={e => setEmail(e.target.value)}
            disabled={busy}
          />
        </label>
        <label className="portal-white-screen-password">
          Account password
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            disabled={busy}
          />
        </label>
        <button
          type="submit"
          className="btn primary"
          disabled={busy || !email.trim() || !password.trim()}>
          {busy ? 'Unlocking…' : 'Unlock website'}
        </button>
        {error ? <p className="portal-white-screen-error">{error}</p> : null}
      </form>
    </div>
  );
}
