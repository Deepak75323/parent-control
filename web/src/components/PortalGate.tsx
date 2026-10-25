import {type ReactNode, useCallback, useEffect, useState} from 'react';
import {portalApi, type PortalControl} from '../api/services';
import {useAuthStore} from '../auth/authStore';

type Props = {children: ReactNode};

/**
 * When the site admin disables the parent portal, render a white screen.
 * Controllers (bootstrap admin or shared parents) still see Enable.
 */
export function PortalGate({children}: Props) {
  const accessToken = useAuthStore(s => s.accessToken);
  const [disabled, setDisabled] = useState(false);
  const [control, setControl] = useState<PortalControl | null>(null);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const statusRes = await portalApi.status();
      const isDisabled = Boolean(statusRes.data.data?.disabled);
      setDisabled(isDisabled);
      if (accessToken) {
        try {
          const controlRes = await portalApi.control();
          setControl(controlRes.data.data ?? null);
        } catch {
          setControl(null);
        }
      } else {
        setControl(null);
      }
      setError(null);
    } catch {
      // If status is unreachable, do not hard-block the app.
      setDisabled(false);
      setError(null);
    } finally {
      setReady(true);
    }
  }, [accessToken]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!disabled) {
      return;
    }
    const id = window.setInterval(() => {
      void refresh();
    }, 5000);
    return () => window.clearInterval(id);
  }, [disabled, refresh]);

  async function enableSite() {
    setBusy(true);
    setError(null);
    try {
      const res = await portalApi.setDisabled(false);
      setControl(res.data.data ?? null);
      setDisabled(Boolean(res.data.data?.disabled));
    } catch {
      setError('Could not enable the website');
    } finally {
      setBusy(false);
    }
  }

  if (!ready) {
    return <div className="portal-white-screen" aria-busy="true" />;
  }

  if (!disabled) {
    return <>{children}</>;
  }

  const canEnable = Boolean(control?.canControl && accessToken);

  return (
    <div className="portal-white-screen" role="status" aria-live="polite">
      {canEnable ? (
        <div className="portal-white-screen-admin">
          <button
            type="button"
            className="btn primary"
            disabled={busy}
            onClick={() => void enableSite()}>
            {busy ? 'Enabling…' : 'Enable website'}
          </button>
          {error ? <p className="portal-white-screen-error">{error}</p> : null}
        </div>
      ) : null}
    </div>
  );
}
