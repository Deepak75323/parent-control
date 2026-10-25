import {useCallback, useEffect, useState} from 'react';
import type {FormEvent} from 'react';
import {useNavigate} from 'react-router-dom';
import {
  authApi,
  deletedMessagesPinApi,
  familyHandleApi,
  parentPinApi,
  portalApi,
  readReceiptsApi,
  sharePresenceApi,
  type PortalControl,
} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {useLogout} from '../auth/useLogout';
import {useConfirmDialog} from '../hooks/useConfirmDialog';
import {chatDisplayNickname} from '../lib/chatDisplayNickname';
import {validateDeletedMessagesPinForm} from '../lib/deletedMessagesPin';
import {FingerprintSettingsCard} from '../components/ParentWebAuthnSetup';
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
  const [deletedPinConfigured, setDeletedPinConfigured] = useState(false);
  const [deletedPinForgot, setDeletedPinForgot] = useState(false);
  const [deletedCurrentPin, setDeletedCurrentPin] = useState('');
  const [deletedPin, setDeletedPin] = useState('');
  const [deletedConfirmPin, setDeletedConfirmPin] = useState('');
  const [deletedPinMsg, setDeletedPinMsg] = useState<string | null>(null);
  const [handle, setHandle] = useState('');
  const [handleStatus, setHandleStatus] = useState<string | null>(null);
  const [readReceiptsEnabled, setReadReceiptsEnabled] = useState(true);
  const [readReceiptsMsg, setReadReceiptsMsg] = useState<string | null>(null);
  const [sharePresenceEnabled, setSharePresenceEnabled] = useState(true);
  const [sharePresenceMsg, setSharePresenceMsg] = useState<string | null>(null);
  const [portalControl, setPortalControl] = useState<PortalControl | null>(null);
  const [portalMsg, setPortalMsg] = useState<string | null>(null);
  const [deletePassword, setDeletePassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const {confirm, dialog: confirmDialog} = useConfirmDialog(busy);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [pinRes, deletedPinRes, handleRes, receiptsRes, presenceRes, portalRes] =
        await Promise.all([
          parentPinApi.status(),
          deletedMessagesPinApi.status(),
          familyHandleApi.status(),
          readReceiptsApi.status(),
          sharePresenceApi.status(),
          portalApi.control().catch(() => null),
        ]);
      setPinConfigured(Boolean(pinRes.data.data?.pinConfigured));
      setDeletedPinConfigured(Boolean(deletedPinRes.data.data?.pinConfigured));
      setHandle(handleRes.data.data?.familyHandle ?? '');
      setHandleStatus(
        handleRes.data.data?.configured
          ? `Configured: ${handleRes.data.data.familyHandle}`
          : 'Not configured',
      );
      setReadReceiptsEnabled(receiptsRes.data.data?.enabled !== false);
      setSharePresenceEnabled(presenceRes.data.data?.enabled !== false);
      setPortalControl(portalRes?.data.data ?? null);
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

  const onSaveDeletedMessagesPin = async (e: FormEvent) => {
    e.preventDefault();
    const mode = deletedPinForgot ? 'reset' : deletedPinConfigured ? 'change' : 'create';
    const validation = validateDeletedMessagesPinForm(mode, {
      currentPin: deletedCurrentPin,
      pin: deletedPin,
      confirmPin: deletedConfirmPin,
    });
    if (validation) {
      setDeletedPinMsg(validation);
      return;
    }
    if (mode === 'reset') {
      const ok = await confirm({
        title: 'Reset deleted messages PIN?',
        message:
          'This permanently clears deleted note history in notebooks you own, then sets your new PIN. This cannot be undone.',
        confirmLabel: 'Clear history and reset PIN',
        danger: true,
      });
      if (!ok) {
        return;
      }
    }
    setBusy(true);
    setDeletedPinMsg(null);
    try {
      if (mode === 'create') {
        await deletedMessagesPinApi.create(deletedPin.trim(), deletedConfirmPin.trim());
        setDeletedPinMsg('Deleted messages PIN saved');
      } else if (mode === 'change') {
        await deletedMessagesPinApi.change(
          deletedCurrentPin.trim(),
          deletedPin.trim(),
          deletedConfirmPin.trim(),
        );
        setDeletedPinMsg('Deleted messages PIN updated');
      } else {
        await deletedMessagesPinApi.reset(deletedPin.trim(), deletedConfirmPin.trim());
        setDeletedPinMsg('PIN reset after clearing deleted history');
        setDeletedPinForgot(false);
      }
      setDeletedPinConfigured(true);
      setDeletedCurrentPin('');
      setDeletedPin('');
      setDeletedConfirmPin('');
    } catch {
      setDeletedPinMsg(
        mode === 'change'
          ? 'Could not change PIN — check the current PIN'
          : mode === 'reset'
            ? 'Could not reset PIN'
            : 'Could not save PIN',
      );
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
      useAuthStore.getState().patchProfile({readReceiptsEnabled: enabled});
      setReadReceiptsMsg(
        enabled
          ? 'Read receipts are on — green ticks when messages are read.'
          : 'Read receipts are off — green read ticks hidden for you and others.',
      );
    } catch {
      setReadReceiptsMsg('Could not update read receipt setting');
    } finally {
      setBusy(false);
    }
  };

  const onToggleSharePresence = async () => {
    const next = !sharePresenceEnabled;
    setBusy(true);
    setSharePresenceMsg(null);
    try {
      const {data} = await sharePresenceApi.setEnabled(next);
      const enabled = data.data?.enabled !== false;
      setSharePresenceEnabled(enabled);
      useAuthStore.getState().patchProfile({sharePresenceEnabled: enabled});
      setSharePresenceMsg(
        enabled
          ? 'Partners can see when you are Online or Last seen.'
          : 'Online / Last seen hidden from chat partners.',
      );
    } catch {
      setSharePresenceMsg('Could not update share presence setting');
    } finally {
      setBusy(false);
    }
  };

  const onTogglePortalDisabled = async () => {
    if (!portalControl?.canControl) {
      return;
    }
    const nextDisabled = !portalControl.disabled;
    const ok = await confirm({
      title: nextDisabled ? 'Disable parent website?' : 'Enable parent website?',
      message: nextDisabled
        ? 'Everyone will see a blank white screen until an admin enables it again.'
        : 'The parent website will become available again for all parents.',
      confirmLabel: nextDisabled ? 'Disable website' : 'Enable website',
      danger: nextDisabled,
    });
    if (!ok) {
      return;
    }
    setBusy(true);
    setPortalMsg(null);
    try {
      const res = await portalApi.setDisabled(nextDisabled);
      setPortalControl(res.data.data ?? null);
      setPortalMsg(nextDisabled ? 'Website disabled — white screen is on' : 'Website enabled');
      if (nextDisabled) {
        window.location.reload();
      }
    } catch {
      setPortalMsg('Could not update website availability');
    } finally {
      setBusy(false);
    }
  };

  const onTogglePortalShare = async () => {
    if (!portalControl?.canShare) {
      return;
    }
    const nextShare = !portalControl.sharedWithEveryone;
    const ok = await confirm({
      title: nextShare ? 'Share control with everyone?' : 'Stop sharing control?',
      message: nextShare
        ? 'Every signed-in parent will be able to disable or enable the website.'
        : 'Only the site admin (anilkumar@gmail.com) will keep this control.',
      confirmLabel: nextShare ? 'Share with everyone' : 'Keep admin only',
      danger: nextShare,
    });
    if (!ok) {
      return;
    }
    setBusy(true);
    setPortalMsg(null);
    try {
      const res = await portalApi.setShareWithEveryone(nextShare);
      setPortalControl(res.data.data ?? null);
      setPortalMsg(
        nextShare ? 'Control shared with every parent' : 'Control limited to site admin again',
      );
    } catch {
      setPortalMsg('Could not update sharing');
    } finally {
      setBusy(false);
    }
  };

  const onDeleteAccount = async (e: FormEvent) => {
    e.preventDefault();
    if (!deletePassword) {
      return;
    }
    if (
      !(await confirm({
        title: 'Delete account',
        message:
          'Permanently delete this parent account? Devices, chat history, and settings cannot be recovered.',
        confirmLabel: 'Delete account',
        danger: true,
      }))
    ) {
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
      {confirmDialog}
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
        <h2>Deleted messages PIN</h2>
        <p className="muted">
          {deletedPinConfigured
            ? deletedPinForgot
              ? 'Forgot PIN clears deleted note history in notebooks you own, then lets you set a new PIN.'
              : 'Required to review deleted notes as a notebook owner. Enter the current PIN to change it.'
            : 'Create a PIN before you can review deleted notes. First time: new PIN and confirm.'}
        </p>
        <form className="form-row" onSubmit={e => void onSaveDeletedMessagesPin(e)}>
          {deletedPinConfigured && !deletedPinForgot ? (
            <label>
              Current PIN
              <input
                type="password"
                inputMode="numeric"
                autoComplete="off"
                value={deletedCurrentPin}
                onChange={e => setDeletedCurrentPin(e.target.value.replace(/\D/g, '').slice(0, 8))}
                maxLength={8}
              />
            </label>
          ) : null}
          <label>
            {deletedPinConfigured ? 'New PIN (4–8 digits)' : 'PIN (4–8 digits)'}
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              value={deletedPin}
              onChange={e => setDeletedPin(e.target.value.replace(/\D/g, '').slice(0, 8))}
              maxLength={8}
            />
          </label>
          <label>
            Confirm PIN
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              value={deletedConfirmPin}
              onChange={e => setDeletedConfirmPin(e.target.value.replace(/\D/g, '').slice(0, 8))}
              maxLength={8}
            />
          </label>
          <div className="btn-row">
            <button type="submit" className="btn primary" disabled={busy}>
              {deletedPinForgot
                ? 'Clear history and reset PIN'
                : deletedPinConfigured
                  ? 'Change PIN'
                  : 'Set PIN'}
            </button>
            {deletedPinConfigured ? (
              <button
                type="button"
                className="btn ghost"
                disabled={busy}
                onClick={() => {
                  setDeletedPinForgot(forgot => !forgot);
                  setDeletedPinMsg(null);
                  setDeletedCurrentPin('');
                  setDeletedPin('');
                  setDeletedConfirmPin('');
                }}>
                {deletedPinForgot ? 'Cancel forgot' : 'Forgot PIN'}
              </button>
            ) : null}
          </div>
          {deletedPinMsg ? <p className="muted">{deletedPinMsg}</p> : null}
        </form>
      </div>

      <FingerprintSettingsCard />

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
          When on, others see when you read their messages and you see when they read yours —
          like WhatsApp. Turn off and neither side gets green read ticks.
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
        <h2>Share presence</h2>
        <p className="muted">
          When on, your partner in a 2-person chat can see Online or Last seen. Turn off to hide
          that from them.
        </p>
        <div className="btn-row">
          <button
            type="button"
            className={`btn ${sharePresenceEnabled ? 'primary' : 'ghost'}`}
            disabled={busy}
            onClick={() => void onToggleSharePresence()}>
            {sharePresenceEnabled ? 'Share presence: On' : 'Share presence: Off'}
          </button>
        </div>
        {sharePresenceMsg ? <p className="muted">{sharePresenceMsg}</p> : null}
      </div>

      {portalControl?.canControl || portalControl?.canShare ? (
        <div className="card">
          <h2>Website availability</h2>
          <p className="muted">
            Site admin can turn the parent website off (blank white screen for everyone) or on
            again. Sharing lets every parent use the same control.
          </p>
          <p className="muted tiny">
            Status:{' '}
            <strong>{portalControl.disabled ? 'Disabled (white screen)' : 'Enabled'}</strong>
            {portalControl.sharedWithEveryone ? ' · Control shared with everyone' : ''}
          </p>
          <div className="btn-row">
            {portalControl.canControl ? (
              <button
                type="button"
                className={`btn ${portalControl.disabled ? 'primary' : 'danger'}`}
                disabled={busy}
                onClick={() => void onTogglePortalDisabled()}>
                {portalControl.disabled ? 'Enable website' : 'Disable website'}
              </button>
            ) : null}
            {portalControl.canShare ? (
              <button
                type="button"
                className={`btn ${portalControl.sharedWithEveryone ? 'ghost' : 'primary'}`}
                disabled={busy}
                onClick={() => void onTogglePortalShare()}>
                {portalControl.sharedWithEveryone
                  ? 'Stop sharing with everyone'
                  : 'Share control with everyone'}
              </button>
            ) : null}
          </div>
          {portalMsg ? <p className="muted">{portalMsg}</p> : null}
        </div>
      ) : null}

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
