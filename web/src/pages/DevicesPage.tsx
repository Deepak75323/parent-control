import {useCallback, useEffect, useMemo, useState} from 'react';
import {Link} from 'react-router-dom';
import {deviceApi} from '../api/services';
import type {DeviceSummary} from '../types/api';
import {deviceInitials, isDevicePresentOnline} from '../lib/devicePresence';
import {appDisplayName} from '../lib/appDisplayName';
import {shouldDismissPairingCodeFromStatus} from '../lib/pairingDetection';
import {useRealtimeStore} from '../realtime/realtimeStore';

function mergeLive(device: DeviceSummary) {
  const live = useRealtimeStore.getState().deviceStatuses[device.id];
  if (!live) {
    return device;
  }
  return {
    ...device,
    status: live.status ?? device.status,
    batteryLevel: live.batteryLevel ?? device.batteryLevel,
    charging: live.charging ?? device.charging,
    networkType: live.networkType ?? device.networkType,
    lastSeenAt: live.lastSeenAt ?? device.lastSeenAt,
    lastLatitude: live.latitude ?? device.lastLatitude,
    lastLongitude: live.longitude ?? device.lastLongitude,
    foregroundApp: live.foregroundApp ?? device.foregroundApp,
  };
}

export function DevicesPage() {
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [pairingCode, setPairingCode] = useState<string | null>(null);
  const [pairingBusy, setPairingBusy] = useState(false);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const deviceStatuses = useRealtimeStore(s => s.deviceStatuses);
  const deviceListEpoch = useRealtimeStore(s => s.deviceListEpoch);
  const removedDeviceIds = useRealtimeStore(s => s.removedDeviceIds);

  const load = useCallback(async () => {
    setError(null);
    try {
      const {data} = await deviceApi.list();
      setDevices(data.data ?? []);
    } catch {
      setError('Failed to load devices');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, deviceListEpoch]);

  useEffect(() => {
    void deviceStatuses;
  }, [deviceStatuses]);

  useEffect(() => {
    const id = window.setInterval(() => setNowMs(Date.now()), 15_000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    if (!pairingCode) {
      return;
    }
    let cancelled = false;
    const tick = async () => {
      try {
        const {data} = await deviceApi.pairingCodeStatus(pairingCode);
        if (cancelled) {
          return;
        }
        if (shouldDismissPairingCodeFromStatus(data.data)) {
          setPairingCode(null);
          void load();
        }
      } catch {
        // ignore
      }
    };
    void tick();
    const id = window.setInterval(() => void tick(), 2500);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [pairingCode, load]);

  const onPair = async () => {
    setPairingBusy(true);
    setError(null);
    try {
      const {data} = await deviceApi.createPairingCode();
      setPairingCode(data.data?.code ?? null);
    } catch {
      setError('Could not create pairing code');
    } finally {
      setPairingBusy(false);
    }
  };

  const list = useMemo(() => {
    void deviceStatuses;
    return devices.filter(d => !removedDeviceIds[d.id]).map(mergeLive);
  }, [devices, removedDeviceIds, deviceStatuses]);
  const active = useMemo(
    () => list.filter(d => (d.status ?? '').toUpperCase() !== 'REVOKED'),
    [list],
  );
  const previouslyLinked = useMemo(
    () => list.filter(d => (d.status ?? '').toUpperCase() === 'REVOKED'),
    [list],
  );
  const onlineCount = active.filter(d =>
    isDevicePresentOnline(d.status, d.lastSeenAt, nowMs),
  ).length;

  return (
    <div className="page">
      <div className="dc-embed">
        <div className="dc-embed-bar" />
        <div className="dc-embed-body">
          <div className="dc-embed-title">Your family</div>
          <div className="dc-embed-fields">
            <div>
              <div className="dc-embed-label">Devices</div>
              <div className="dc-embed-value">{active.length}</div>
            </div>
            <div>
              <div className="dc-embed-label">Online</div>
              <div className="dc-embed-value ok">{onlineCount}</div>
            </div>
            <div>
              <div className="dc-embed-label">Offline</div>
              <div className="dc-embed-value">{Math.max(0, active.length - onlineCount)}</div>
            </div>
          </div>
          <div className="dc-embed-actions">
            <button
              type="button"
              className="btn primary"
              disabled={pairingBusy}
              onClick={() => void onPair()}>
              {pairingBusy ? 'Creating…' : 'Add device'}
            </button>
          </div>
        </div>
      </div>

      {pairingCode ? (
        <div className="dc-embed accent">
          <div className="dc-embed-bar" />
          <div className="dc-embed-body">
            <div className="dc-embed-title">Pairing code</div>
            <div className="pairing-code">{pairingCode}</div>
            <p className="muted">Waiting for the child phone to enter this code…</p>
            <button type="button" className="btn ghost" onClick={() => setPairingCode(null)}>
              Dismiss
            </button>
          </div>
        </div>
      ) : null}

      {error ? <div className="error">{error}</div> : null}
      {loading ? <p className="muted">Loading devices…</p> : null}

      {!loading && active.length === 0 ? (
        <div className="dc-empty">
          <div className="dc-empty-icon">📱</div>
          <h3>No devices connected</h3>
          <p className="muted">
            Generate a pairing code and enter it on your child&apos;s phone to start monitoring.
          </p>
          <button type="button" className="btn primary" disabled={pairingBusy} onClick={() => void onPair()}>
            {pairingBusy ? 'Creating…' : 'Pair a device'}
          </button>
        </div>
      ) : null}

      <div className="dc-msg-list">
        {active.map(d => {
          const online = isDevicePresentOnline(d.status, d.lastSeenAt, nowMs);
          const revoked = (d.status ?? '').toUpperCase() === 'REVOKED';
          const appLabel = d.foregroundApp
            ? appDisplayName({packageName: d.foregroundApp})
            : null;
          return (
            <Link key={d.id} to={`/devices/${d.id}`} className="dc-msg">
              <div className={`dc-msg-avatar ${online ? 'online' : ''}`}>
                {deviceInitials(d.deviceName)}
                <span className={`dc-status-dot ${revoked ? 'dnd' : online ? 'online' : 'idle'}`} />
              </div>
              <div className="dc-msg-body">
                <div className="dc-msg-head">
                  <span className="dc-msg-author">{d.deviceName}</span>
                  <span className="dc-msg-time">
                    {d.lastSeenAt ? new Date(d.lastSeenAt).toLocaleString() : 'Never seen'}
                  </span>
                </div>
                <div className="dc-msg-text">
                  <span className={`status-pill ${revoked ? 'revoked' : online ? 'online' : 'offline'}`}>
                    {revoked ? 'Revoked' : online ? 'Online' : 'Offline'}
                  </span>
                  {d.batteryLevel != null && !revoked ? (
                    <span className="muted-inline"> · {d.batteryLevel}% battery</span>
                  ) : null}
                  {d.networkType ? <span className="muted-inline"> · {d.networkType}</span> : null}
                  {appLabel ? <span className="muted-inline"> · {appLabel}</span> : null}
                </div>
              </div>
            </Link>
          );
        })}
      </div>

      {previouslyLinked.length > 0 ? (
        <>
          <div className="dc-date-sep">
            <span>Previously linked</span>
          </div>
          <div className="dc-msg-list">
            {previouslyLinked.map(d => (
              <Link key={d.id} to={`/devices/${d.id}`} className="dc-msg soft">
                <div className="dc-msg-avatar">
                  {deviceInitials(d.deviceName)}
                  <span className="dc-status-dot dnd" />
                </div>
                <div className="dc-msg-body">
                  <div className="dc-msg-head">
                    <span className="dc-msg-author">{d.deviceName}</span>
                  </div>
                  <div className="dc-msg-text">
                    <span className="status-pill revoked">Revoked</span>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
}
