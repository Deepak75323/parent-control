import {useCallback, useEffect, useState} from 'react';
import {deviceApi} from '../api/services';
import {deviceInitials, isDevicePresentOnline} from '../lib/devicePresence';
import {revocableDevices} from '../lib/removePairing';
import {useRealtimeStore} from '../realtime/realtimeStore';
import type {DeviceSummary} from '../types/api';

export function RemovePairingSettingsCard() {
  const [enabled, setEnabled] = useState(false);
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [selected, setSelected] = useState<DeviceSummary | null>(null);
  const [password, setPassword] = useState('');
  const liveStatuses = useRealtimeStore(s => s.deviceStatuses);
  const removeDevice = useRealtimeStore(s => s.removeDevice);

  const loadDevices = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const {data} = await deviceApi.list();
      setDevices(revocableDevices(data.data ?? []));
    } catch {
      setError('Could not load paired devices');
      setDevices([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!enabled) {
      setSelected(null);
      setPassword('');
      setMsg(null);
      return;
    }
    void loadDevices();
  }, [enabled, loadDevices]);

  const openRevoke = (device: DeviceSummary) => {
    setSelected(device);
    setPassword('');
    setError(null);
    setMsg(null);
  };

  const closeRevoke = () => {
    if (busy) {
      return;
    }
    setSelected(null);
    setPassword('');
  };

  const onConfirmRevoke = async () => {
    if (!selected) {
      return;
    }
    if (!password.trim()) {
      setError('Enter your account password to remove pairing');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await deviceApi.sendCommand(selected.id, 'REVOKE_MONITORING', undefined, {
        password,
      });
      removeDevice(selected.id);
      setDevices(prev => prev.filter(d => d.id !== selected.id));
      setMsg(`Removed pairing for ${selected.deviceName}`);
      setSelected(null);
      setPassword('');
    } catch (err: unknown) {
      const apiMsg =
        (err as {response?: {data?: {message?: string}}})?.response?.data?.message ??
        'Could not remove pairing — check password and try again';
      setError(apiMsg);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card danger-zone">
      <h2>Remove pairing</h2>
      <p className="muted">
        Off by default. Turn on to see paired child phones, then tap one and confirm with your
        account password to stop monitoring and free the plan seat.
      </p>
      <div className="btn-row">
        <button
          type="button"
          className={`btn ${enabled ? 'danger' : 'ghost'}`}
          disabled={busy}
          onClick={() => setEnabled(v => !v)}>
          {enabled ? 'Remove pairing: On' : 'Remove pairing: Off'}
        </button>
      </div>

      {enabled ? (
        <div className="remove-pairing-panel">
          {loading ? <p className="muted">Loading devices…</p> : null}
          {!loading && devices.length === 0 ? (
            <p className="muted">No active pairings to remove.</p>
          ) : null}
          {!loading && devices.length > 0 ? (
            <ul className="list remove-pairing-list">
              {devices.map(device => {
                const live = liveStatuses[device.id];
                const status = live?.status ?? device.status;
                const lastSeenAt = live?.lastSeenAt ?? device.lastSeenAt;
                const online = isDevicePresentOnline(status, lastSeenAt);
                return (
                  <li key={device.id}>
                    <button
                      type="button"
                      className="remove-pairing-row"
                      disabled={busy}
                      onClick={() => openRevoke(device)}>
                      <span className={`dc-msg-avatar compact ${online ? 'online' : ''}`}>
                        {deviceInitials(device.deviceName)}
                        <span className={`dc-status-dot ${online ? 'online' : 'idle'}`} />
                      </span>
                      <span className="remove-pairing-row-body">
                        <strong>{device.deviceName}</strong>
                        <span className="tiny muted">
                          {online ? 'Online' : 'Offline'} · tap to remove
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          ) : null}
        </div>
      ) : null}

      {msg ? <p className="muted">{msg}</p> : null}
      {error && !selected ? <p className="error">{error}</p> : null}

      {selected ? (
        <div
          className="confirm-dialog-overlay"
          role="presentation"
          onClick={event => {
            if (event.target === event.currentTarget) {
              closeRevoke();
            }
          }}>
          <div
            className="confirm-dialog"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="settings-revoke-pairing-title">
            <h2 id="settings-revoke-pairing-title" className="confirm-dialog-title">
              Remove pairing?
            </h2>
            <p className="confirm-dialog-message">
              Stops monitoring on <strong>{selected.deviceName}</strong>, frees the plan seat, and
              marks the device as revoked. Confirm with your account password.
            </p>
            <label className="confirm-dialog-password">
              Account password
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                disabled={busy}
                autoFocus
              />
            </label>
            {error ? <p className="error">{error}</p> : null}
            <div className="confirm-dialog-actions">
              <button type="button" className="btn ghost" disabled={busy} onClick={closeRevoke}>
                Cancel
              </button>
              <button
                type="button"
                className="btn danger"
                disabled={busy || !password.trim()}
                onClick={() => void onConfirmRevoke()}>
                {busy ? 'Removing…' : 'Remove pairing'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
