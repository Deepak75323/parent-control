import {useCallback, useEffect, useState} from 'react';
import type {FormEvent} from 'react';
import {useNavigate} from 'react-router-dom';
import {
  authApi,
  familyHandleApi,
  parentPinApi,
  readReceiptsApi,
} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {useLogout} from '../auth/useLogout';
import {chatDisplayNickname} from '../lib/chatDisplayNickname';
import {disconnectWebSocket} from '../realtime/websocket';
import {useRealtimeStore} from '../realtime/realtimeStore';

export function SettingsPage() {
  const profile = useAuthStore(s => s.profile);
  const email = useAuthStore(s => s.email);
  const clear = useAuthStore(s => s.clear);
  const navigate = useNavigate();
  const onLogout = useLogout();
  const accountEmail = profile?.email ?? email ?? '';
  const chatNickname =
    profile?.chatNickname?.trim() ||
    (accountEmail ? chatDisplayNickname(accountEmail) : 'Unknown Parent');

  const [pinConfigured, setPinConfigured] = useState(false);
  const [pin, setPin] = useState('');
  const [pinMsg, setPinMsg] = useState<string | null>(null);
  const [handle, setHandle] = useState('');
  const [handleStatus, setHandleStatus] = useState<string | null>(null);
  const [readReceiptsEnabled, setReadReceiptsEnabled] = useState(true);
  const [readReceiptsMsg, setReadReceiptsMsg] = useState<string | null>(null);
  const [deletePassword, setDeletePassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [pinRes, handleRes, receiptsRes] = await Promise.all([
        parentPinApi.status(),
        familyHandleApi.status(),
        readReceiptsApi.status(),
      ]);
      setPinConfigured(Boolean(pinRes.data.data?.pinConfigured));
      setHandle(handleRes.data.data?.familyHandle ?? '');
      setHandleStatus(
        handleRes.data.data?.configured
          ? `Configured: ${handleRes.data.data.familyHandle}`
          : 'Not configured',
      );
      setReadReceiptsEnabled(receiptsRes.data.data?.enabled !== false);
    } catch {
      setError('Failed to load settings');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onSetPin = async (e: FormEvent) => {
    e.preventDefault();
    if (!/^\d{4,8}$/.test(pin)) {
      setPinMsg('PIN must be 4–8 digits');
      return;
    }
    setBusy(true);
    setPinMsg(null);
    try {
      await parentPinApi.setPin(pin);
      setPin('');
      setPinConfigured(true);
      setPinMsg('PIN saved');
    } catch {
      setPinMsg('Could not save PIN');
    } finally {
      setBusy(false);
    }
  };

  const onVerifyPin = async () => {
    if (!pin) {
      return;
    }
    setBusy(true);
    setPinMsg(null);
    try {
      const {data} = await parentPinApi.verify(pin);
      setPinMsg(data.data?.verified ? 'PIN verified' : 'Incorrect PIN');
    } catch {
      setPinMsg('Verify failed');
    } finally {
      setBusy(false);
    }
  };

  const onSaveHandle = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const {data} = await familyHandleApi.setHandle(handle.trim());
      setHandleStatus(
        data.data?.configured ? `Configured: ${data.data.familyHandle}` : 'Not configured',
      );
    } catch {
      setError('Could not save family handle');
    } finally {
      setBusy(false);
    }
  };

  const onToggleReadReceipts = async () => {
    const next = !readReceiptsEnabled;
    setBusy(true);
    setReadReceiptsMsg(null);
    try {
      const {data} = await readReceiptsApi.setEnabled(next);
      const enabled = data.data?.enabled !== false;
      setReadReceiptsEnabled(enabled);
      setReadReceiptsMsg(
        enabled
          ? 'Read receipts are on — others will see when you read their messages.'
          : 'Read receipts are off — others will not see when you read their messages.',
      );
    } catch {
      setReadReceiptsMsg('Could not update read receipt setting');
    } finally {
      setBusy(false);
    }
  };

  const onDeleteAccount = async (e: FormEvent) => {
    e.preventDefault();
    if (!deletePassword || !window.confirm('Permanently delete this parent account?')) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await authApi.deleteAccount(deletePassword);
      disconnectWebSocket();
      useRealtimeStore.getState().reset();
      clear();
      navigate('/login', {replace: true});
    } catch {
      setError('Account deletion failed — check password');
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Settings</h1>
          <p className="muted">Account, parent PIN, family handle, and chat nickname.</p>
        </div>
      </div>
      {error ? <div className="error">{error}</div> : null}

      <div className="card">
        <h2>Profile</h2>
        <dl className="meta">
          <div>
            <dt>Name</dt>
            <dd>
              {[profile?.firstName, profile?.lastName].filter(Boolean).join(' ') || '—'}
            </dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{profile?.email ?? email ?? '—'}</dd>
          </div>
          <div>
            <dt>Email verified</dt>
            <dd>{profile?.emailVerified ? 'Yes' : 'No'}</dd>
          </div>
          <div>
            <dt>Chat nickname</dt>
            <dd>{chatNickname}</dd>
          </div>
        </dl>
        <p className="muted tiny">
          Assigned from your email — unique and stable. Shown in chat instead of your @handle.
        </p>
      </div>

      <div className="card">
        <h2>Parent PIN</h2>
        <p className="muted">
          {pinConfigured
            ? 'PIN is configured. Required for silent screen streaming.'
            : 'No PIN yet — set one to unlock silent screen share.'}
        </p>
        <form className="form-row" onSubmit={e => void onSetPin(e)}>
          <label>
            PIN (4–8 digits)
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              value={pin}
              onChange={e => setPin(e.target.value)}
              maxLength={8}
            />
          </label>
          <div className="btn-row">
            <button type="submit" className="btn primary" disabled={busy}>
              {pinConfigured ? 'Update PIN' : 'Set PIN'}
            </button>
            <button type="button" className="btn ghost" disabled={busy} onClick={() => void onVerifyPin()}>
              Verify
            </button>
          </div>
          {pinMsg ? <p className="muted">{pinMsg}</p> : null}
        </form>
      </div>

      <div className="card">
        <h2>Family handle</h2>
        <p className="muted">{handleStatus}</p>
        <form className="form-row" onSubmit={e => void onSaveHandle(e)}>
          <label>
            Handle
            <input value={handle} onChange={e => setHandle(e.target.value)} maxLength={32} />
          </label>
          <button type="submit" className="btn primary" disabled={busy}>
            Save handle
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Chat read receipts</h2>
        <p className="muted">
          When enabled, other parents see blue ticks when you receive their messages and green
          ticks when you read them — like WhatsApp. Turn off to hide your read status.
        </p>
        <div className="btn-row">
          <button
            type="button"
            className={`btn ${readReceiptsEnabled ? 'primary' : 'ghost'}`}
            disabled={busy}
            onClick={() => void onToggleReadReceipts()}>
            {readReceiptsEnabled ? 'Read receipts: On' : 'Read receipts: Off'}
          </button>
        </div>
        {readReceiptsMsg ? <p className="muted">{readReceiptsMsg}</p> : null}
      </div>

      <div className="card">
        <h2>Session</h2>
        <p className="muted">Sign out of the web portal on this browser.</p>
        <button type="button" className="btn ghost" onClick={() => void onLogout()}>
          Log out
        </button>
      </div>

      <div className="card danger-zone">
        <h2>Delete account</h2>
        <p className="muted">Removes this parent account and unpairs devices. Irreversible.</p>
        <form className="form-row" onSubmit={e => void onDeleteAccount(e)}>
          <label>
            Confirm with password
            <input
              type="password"
              autoComplete="current-password"
              value={deletePassword}
              onChange={e => setDeletePassword(e.target.value)}
            />
          </label>
          <button type="submit" className="btn danger" disabled={busy || !deletePassword}>
            Delete account
          </button>
        </form>
      </div>
    </div>
  );
}
