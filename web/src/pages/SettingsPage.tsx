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
import {CameraPasswordSettingsCard} from '../components/CameraPasswordSettingsCard';
import {FingerprintSettingsCard} from '../components/ParentWebAuthnSetup';
import {RemovePairingSettingsCard} from '../components/RemovePairingSettingsCard';
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
  const [portalPassword, setPortalPassword] = useState('');
  const [portalShareHandle, setPortalShareHandle] = useState('');
  const [deletePassword, setDeletePassword] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [passwordMsg, setPasswordMsg] = useState<string | null>(null);
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
          ? 'Read receipts are on — blue ticks when messages are read.'
          : 'Read receipts are off — blue read ticks hidden for you and others (gray ticks still mean delivered).',
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
    if (!portalPassword.trim()) {
      setPortalMsg('Enter your account password to continue');
      return;
    }
    const nextDisabled = !portalControl.disabled;
    const ok = await confirm({
      title: nextDisabled ? 'Disable parent website?' : 'Enable parent website?',
      message: nextDisabled
        ? 'Everyone will see a blank white screen until someone with control enables it again.'
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
      const res = await portalApi.setDisabled(nextDisabled, portalPassword);
      setPortalControl(res.data.data ?? null);
      setPortalPassword('');
      setPortalMsg(nextDisabled ? 'Website disabled — white screen is on' : 'Website enabled');
      if (nextDisabled) {
        window.location.reload();
      }
    } catch {
      setPortalMsg('Could not update website availability — check password');
    } finally {
      setBusy(false);
    }
  };

  const onGrantPortalHandle = async (e: FormEvent) => {
    e.preventDefault();
    if (!portalControl?.canShare) {
      return;
    }
    if (!portalShareHandle.trim()) {
      setPortalMsg('Enter a family handle to share control');
      return;
    }
    if (!portalPassword.trim()) {
      setPortalMsg('Enter your account password to continue');
      return;
    }
    setBusy(true);
    setPortalMsg(null);
    try {
      const res = await portalApi.grantOperator(portalShareHandle.trim(), portalPassword);
      setPortalControl(res.data.data ?? null);
      setPortalShareHandle('');
      setPortalPassword('');
      setPortalMsg('Control shared with that family handle');
    } catch {
      setPortalMsg('Could not share control — check handle and password');
    } finally {
      setBusy(false);
    }
  };

  const onRevokePortalOperator = async (userId: string, familyHandle: string) => {
    if (!portalControl?.canShare) {
      return;
    }
    if (!portalPassword.trim()) {
      setPortalMsg('Enter your account password to continue');
      return;
    }
    const ok = await confirm({
      title: 'Revoke website control?',
      message: `Remove kill-switch access from @${familyHandle}?`,
      confirmLabel: 'Revoke',
      danger: true,
    });
    if (!ok) {
      return;
    }
    setBusy(true);
    setPortalMsg(null);
    try {
      const res = await portalApi.revokeOperator(userId, portalPassword);
      setPortalControl(res.data.data ?? null);
      setPortalPassword('');
      setPortalMsg(`Revoked control from @${familyHandle}`);
    } catch {
      setPortalMsg('Could not revoke — check password');
    } finally {
      setBusy(false);
    }
  };

  const onChangePassword = async (e: FormEvent) => {
    e.preventDefault();
    setPasswordMsg(null);
    if (newPassword.length < 8) {
      setPasswordMsg('New password must be at least 8 characters');
      return;
    }
    if (newPassword !== confirmNewPassword) {
      setPasswordMsg('New password and confirmation do not match');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await authApi.changePassword(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmNewPassword('');
      disconnectWebSocket();
      useRealtimeStore.getState().reset();
      clear();
      navigate('/login', {replace: true});
    } catch (err: unknown) {
      const msg =
        (err as {response?: {data?: {message?: string}}})?.response?.data?.message ??
        'Could not change password — check your current password';
      setPasswordMsg(msg);
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

      <CameraPasswordSettingsCard />

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
            Turn the parent website off (blank white screen) or on again. Requires your account
            password. Share control with a specific parent by their family handle.
          </p>
          <p className="muted tiny">
            Status:{' '}
            <strong>{portalControl.disabled ? 'Disabled (white screen)' : 'Enabled'}</strong>
          </p>
          <label>
            Account password
            <input
              type="password"
              autoComplete="current-password"
              value={portalPassword}
              onChange={e => setPortalPassword(e.target.value)}
              placeholder="Required for these actions"
            />
          </label>
          {portalControl.canControl ? (
            <div className="btn-row">
              <button
                type="button"
                className={`btn ${portalControl.disabled ? 'primary' : 'danger'}`}
                disabled={busy || !portalPassword}
                onClick={() => void onTogglePortalDisabled()}>
                {portalControl.disabled ? 'Enable website' : 'Disable website'}
              </button>
            </div>
          ) : null}
          {portalControl.canShare ? (
            <>
              <form className="form-row" onSubmit={e => void onGrantPortalHandle(e)}>
                <label>
                  Share control by handle
                  <input
                    value={portalShareHandle}
                    onChange={e => setPortalShareHandle(e.target.value)}
                    placeholder="@family.handle"
                    maxLength={32}
                  />
                </label>
                <button
                  type="submit"
                  className="btn primary"
                  disabled={busy || !portalPassword || !portalShareHandle.trim()}>
                  Share with handle
                </button>
              </form>
              {(portalControl.operators?.length ?? 0) > 0 ? (
                <ul className="portal-operators-list">
                  {portalControl.operators.map(op => (
                    <li key={op.userId} className="portal-operators-row">
                      <span>@{op.familyHandle}</span>
                      <button
                        type="button"
                        className="btn ghost tiny"
                        disabled={busy || !portalPassword}
                        onClick={() => void onRevokePortalOperator(op.userId, op.familyHandle)}>
                        Revoke
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="muted tiny">No other parents have control yet.</p>
              )}
            </>
          ) : null}
          {portalMsg ? <p className="muted">{portalMsg}</p> : null}
        </div>
      ) : null}

      <div className="card">
        <h2>Change password</h2>
        <p className="muted">
          Updates your sign-in password. You will be signed out on all devices afterward.
        </p>
        <form onSubmit={e => void onChangePassword(e)}>
          <label>
            Current password
            <input
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={e => setCurrentPassword(e.target.value)}
              required
            />
          </label>
          <label>
            New password (min 8 characters)
            <input
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              minLength={8}
              required
            />
          </label>
          <label>
            Confirm new password
            <input
              type="password"
              autoComplete="new-password"
              value={confirmNewPassword}
              onChange={e => setConfirmNewPassword(e.target.value)}
              minLength={8}
              required
            />
          </label>
          <div className="btn-row">
            <button
              type="submit"
              className="btn primary"
              disabled={busy || !currentPassword || !newPassword || !confirmNewPassword}>
              Update password
            </button>
          </div>
        </form>
        {passwordMsg ? <p className="muted">{passwordMsg}</p> : null}
      </div>

      <div className="card">
        <h2>Session</h2>
        <p className="muted">Sign out of the web portal on this browser.</p>
        <button type="button" className="btn ghost" onClick={() => void onLogout()}>
          Log out
        </button>
      </div>

      <RemovePairingSettingsCard />

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
