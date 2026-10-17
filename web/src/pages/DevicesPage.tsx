import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import {Link, useLocation} from 'react-router-dom';
import {deviceApi} from '../api/services';
import type {DeviceSummary} from '../types/api';
import {deviceInitials, isDevicePresentOnline} from '../lib/devicePresence';
import {appDisplayName} from '../lib/appDisplayName';
import {shouldDismissPairingSession} from '../lib/pairingDetection';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {useMediaQuery} from '../hooks/useMediaQuery';
import {DeviceDetailsPanel} from '../components/DeviceDetailsPanel';
import {lockBodyScroll} from '../lib/scrollLock';

type PairingBaseline = {
  ids: Set<string>;
  statuses: Record<string, string>;
  startedAt: number;
};

function snapshotBaseline(list: DeviceSummary[]): PairingBaseline {
  return {
    ids: new Set(list.map(d => d.id)),
    statuses: Object.fromEntries(list.map(d => [d.id, (d.status ?? '').toUpperCase()])),
    startedAt: Date.now(),
  };
}

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
  const location = useLocation();
  const isMobile = useMediaQuery('(max-width: 639px)');
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [pairingCode, setPairingCode] = useState<string | null>(null);
  const [pairingBusy, setPairingBusy] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list');
  const [sheetDevice, setSheetDevice] = useState<DeviceSummary | null>(null);
  const pairingBaselineRef = useRef<PairingBaseline | null>(null);
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
    if (!sheetDevice) {
      return;
    }
    return lockBodyScroll();
  }, [sheetDevice]);

  useEffect(() => {
    if (!isMobile) {
      setSheetDevice(null);
    }
  }, [isMobile]);

  const dismissPairingIfComplete = useCallback(
    async (list: DeviceSummary[]) => {
      const code = pairingCode;
      const baseline = pairingBaselineRef.current;
      if (!code || !baseline) {
        return false;
      }
      try {
        const {data} = await deviceApi.pairingCodeStatus(code);
        if (
          shouldDismissPairingSession(
            data.data,
            list,
            baseline.ids,
            baseline.statuses,
            baseline.startedAt,
          )
        ) {
          setPairingCode(null);
          pairingBaselineRef.current = null;
          return true;
        }
      } catch {
        if (
          shouldDismissPairingSession(
            null,
            list,
            baseline.ids,
            baseline.statuses,
            baseline.startedAt,
          )
        ) {
          setPairingCode(null);
          pairingBaselineRef.current = null;
          return true;
        }
      }
      return false;
    },
    [pairingCode],
  );

  useEffect(() => {
    if (!pairingCode) {
      return;
    }
    let cancelled = false;
    const tick = async () => {
      try {
        const {data: listResp} = await deviceApi.list();
        if (cancelled) {
          return;
        }
        const list = listResp.data ?? [];
        setDevices(list);
        if (await dismissPairingIfComplete(list)) {
          return;
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
  }, [pairingCode, dismissPairingIfComplete]);

  useEffect(() => {
    const baseline = pairingBaselineRef.current;
    if (!pairingCode || !baseline) {
      return;
    }
    if (
      shouldDismissPairingSession(null, devices, baseline.ids, baseline.statuses, baseline.startedAt)
    ) {
      setPairingCode(null);
      pairingBaselineRef.current = null;
    }
  }, [devices, deviceListEpoch, pairingCode]);

  const onPair = async () => {
    setPairingBusy(true);
    setError(null);
    try {
      const {data: listResp} = await deviceApi.list();
      const baselineList = listResp.data ?? [];
      setDevices(baselineList);
      pairingBaselineRef.current = snapshotBaseline(baselineList);
      const {data} = await deviceApi.createPairingCode();
      setPairingCode(data.data?.code ?? null);
    } catch {
      pairingBaselineRef.current = null;
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
  const query = searchQuery.trim().toLowerCase();
  const filteredActive = useMemo(() => {
    if (!query) {
      return active;
    }
    return active.filter(d => (d.deviceName ?? '').toLowerCase().includes(query));
  }, [active, query]);
  const selectedId = location.pathname.match(/^\/devices\/([^/]+)/)?.[1] ?? null;

  const renderDeviceCard = (d: DeviceSummary, soft = false) => {
    const online = isDevicePresentOnline(d.status, d.lastSeenAt, nowMs);
    const revoked = (d.status ?? '').toUpperCase() === 'REVOKED';
    const appLabel = d.foregroundApp
      ? appDisplayName({packageName: d.foregroundApp})
      : null;
    const selected = selectedId === d.id;
    const className = `device-card${selected ? ' device-card--selected' : ''}${soft ? ' soft' : ''}`;

    return (
      <Link key={d.id} to={`/devices/${d.id}`} className={className}>
        <div className={`device-card-avatar ${online ? 'online' : ''}`}>
          {deviceInitials(d.deviceName)}
          <span className={`dc-status-dot ${revoked ? 'dnd' : online ? 'online' : 'idle'}`} />
        </div>
        <div className="device-card-body">
          <div className="device-card-title-row">
            <div className="device-card-title-stack">
              <span className="device-card-name">{d.deviceName}</span>
              <span className={`device-card-status-inline ${online ? 'online' : 'offline'}`}>
                {revoked ? 'Revoked' : online ? 'Online' : 'Offline'}
              </span>
            </div>
            {isMobile && !soft ? (
              <button
                type="button"
                className="device-card-chevron device-card-chevron-btn"
                aria-label="Device quick actions"
                onClick={event => {
                  event.preventDefault();
                  event.stopPropagation();
                  setSheetDevice(d);
                }}>
                ›
              </button>
            ) : (
              <span className="device-card-chevron" aria-hidden>
                ›
              </span>
            )}
          </div>
          <div className="device-card-meta">
            <span className="device-card-meta-label">Last synced</span>
            <span className="device-card-meta-value">
              {d.lastSeenAt ? new Date(d.lastSeenAt).toLocaleString() : 'Never seen'}
            </span>
          </div>
          <div className="device-card-status-row">
            <span className={`status-pill ${revoked ? 'revoked' : online ? 'online' : 'offline'}`}>
              {revoked ? 'Revoked' : online ? 'Online' : 'Offline'}
            </span>
            {d.batteryLevel != null && !revoked ? (
              <span className="device-card-chip">{d.batteryLevel}% battery</span>
            ) : null}
            {d.networkType ? <span className="device-card-chip">{d.networkType}</span> : null}
            {appLabel ? <span className="device-card-chip">{appLabel}</span> : null}
          </div>
        </div>
      </Link>
    );
  };

  return (
    <div className="page devices-page">
      <section className="family-summary-card">
        <div className="family-summary-head">
          <div className="family-summary-title-row">
            <span className="family-summary-icon" aria-hidden>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                <rect x="5" y="2" width="14" height="20" rx="2" />
                <path d="M12 18h.01" />
              </svg>
            </span>
            <div className="family-summary-copy">
              <h2 className="family-summary-title">Your family</h2>
              <p className="family-summary-sub">Overview of all connected devices.</p>
            </div>
          </div>
          <button
            type="button"
            className="btn primary family-summary-cta"
            disabled={pairingBusy}
            onClick={() => void onPair()}>
            {pairingBusy ? 'Creating…' : '+ Add device'}
          </button>
        </div>
        <div className="family-summary-stats">
          <div className="family-stat">
            <div className="family-stat-label">Devices</div>
            <div className="family-stat-value">{active.length}</div>
          </div>
          <div className="family-stat">
            <div className="family-stat-label">Online</div>
            <div className="family-stat-value family-stat-value--online">{onlineCount}</div>
          </div>
          <div className="family-stat">
            <div className="family-stat-label">Offline</div>
            <div className="family-stat-value">{Math.max(0, active.length - onlineCount)}</div>
          </div>
        </div>
      </section>

      {pairingCode ? (
        <div className="family-summary-card family-summary-card--accent">
          <div className="family-summary-title">Pairing code</div>
          <div className="pairing-code">{pairingCode}</div>
          <p className="muted">Waiting for the child phone to enter this code…</p>
          <button
            type="button"
            className="btn ghost"
            onClick={() => {
              setPairingCode(null);
              pairingBaselineRef.current = null;
            }}>
            Dismiss
          </button>
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

      {active.length > 0 ? (
        <section className="devices-section">
          <h3 className="devices-section-title">Devices ({filteredActive.length})</h3>
          <div className="devices-section-tools">
            <label className="devices-search">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
                <circle cx="11" cy="11" r="7" />
                <path d="M20 20l-3-3" />
              </svg>
              <input
                type="search"
                placeholder="Search devices..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                aria-label="Search devices"
              />
            </label>
            <div className="devices-view-toggle" role="group" aria-label="View mode">
              <button
                type="button"
                className={viewMode === 'list' ? 'active' : ''}
                aria-pressed={viewMode === 'list'}
                onClick={() => setViewMode('list')}
                title="List view">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
                  <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
                </svg>
              </button>
              <button
                type="button"
                className={viewMode === 'grid' ? 'active' : ''}
                aria-pressed={viewMode === 'grid'}
                onClick={() => setViewMode('grid')}
                title="Grid view">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
                  <rect x="3" y="3" width="7" height="7" rx="1" />
                  <rect x="14" y="3" width="7" height="7" rx="1" />
                  <rect x="3" y="14" width="7" height="7" rx="1" />
                  <rect x="14" y="14" width="7" height="7" rx="1" />
                </svg>
              </button>
            </div>
          </div>
          <div className={`device-card-list${viewMode === 'grid' ? ' device-card-list--grid' : ''}`}>
            {filteredActive.map(d => renderDeviceCard(d))}
          </div>
        </section>
      ) : null}

      {previouslyLinked.length > 0 ? (
        <section className="devices-section">
          <h3 className="devices-section-title">Previously linked</h3>
          <div className="device-card-list">
            {previouslyLinked.map(d => renderDeviceCard(d, true))}
          </div>
        </section>
      ) : null}

      {sheetDevice && typeof document !== 'undefined'
        ? createPortal(
            <div className="device-bottom-sheet" role="dialog" aria-modal="true" aria-label="Device details">
              <button
                type="button"
                className="device-bottom-sheet-backdrop"
                aria-label="Close device details"
                onClick={() => setSheetDevice(null)}
              />
              <div className="device-bottom-sheet-panel">
                <div className="device-bottom-sheet-handle" aria-hidden />
                <div className="device-bottom-sheet-head">
                  <span className="device-bottom-sheet-title">Device</span>
                  <button
                    type="button"
                    className="device-bottom-sheet-close"
                    aria-label="Close"
                    onClick={() => setSheetDevice(null)}>
                    ×
                  </button>
                </div>
                <div className="device-bottom-sheet-body">
                  <DeviceDetailsPanel
                    device={sheetDevice}
                    nowMs={nowMs}
                    onlineCount={onlineCount}
                  />
                </div>
              </div>
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}
