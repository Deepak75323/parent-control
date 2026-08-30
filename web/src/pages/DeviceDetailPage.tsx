import {useCallback, useEffect, useMemo, useState} from 'react';
import {Link, useParams} from 'react-router-dom';
import {deviceApi, monitoringApi} from '../api/services';
import {AppIcon} from '../components/AppIcon';
import {LiveStreamPanel} from '../components/LiveStreamPanel';
import {appDisplayName} from '../lib/appDisplayName';
import {deviceInitials, isDevicePresentOnline} from '../lib/devicePresence';
import type {
  DeviceSummary,
  InstalledAppItem,
  LocationPoint,
  NotificationItem,
  UsageStatsItem,
} from '../types/api';
import {useRealtimeStore} from '../realtime/realtimeStore';

function formatUsage(ms: number): string {
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const rem = minutes % 60;
  return rem ? `${hours}h ${rem}m` : `${hours}h`;
}

function sleep(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms));
}

function appsHaveIcons(list: InstalledAppItem[]): boolean {
  return list.some(a => Boolean(a.iconBase64 && a.iconBase64.length > 20));
}

export function DeviceDetailPage() {
  const {deviceId = ''} = useParams();
  const [device, setDevice] = useState<DeviceSummary | null>(null);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [locations, setLocations] = useState<LocationPoint[]>([]);
  const [apps, setApps] = useState<InstalledAppItem[]>([]);
  const [usage, setUsage] = useState<UsageStatsItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [syncingIcons, setSyncingIcons] = useState(false);
  const live = useRealtimeStore(s => s.deviceStatuses[deviceId]);
  const liveNotifs = useRealtimeStore(s => s.liveNotifications);
  const liveLoc = useRealtimeStore(s => s.liveLocations[deviceId]);

  const load = useCallback(async () => {
    if (!deviceId) {
      return;
    }
    setError(null);
    try {
      const [dev, notif, loc, installed, stats] = await Promise.all([
        deviceApi.get(deviceId),
        monitoringApi.notifications(deviceId, 0, 20),
        monitoringApi.locationHistory(deviceId, 0, 20),
        monitoringApi.installedApps(deviceId),
        monitoringApi.usageStats(deviceId, 0, 40),
      ]);
      setDevice(dev.data.data);
      setNotifications(notif.data.data?.content ?? []);
      setLocations(loc.data.data?.content ?? []);
      const installedList = installed.data.data ?? [];
      setApps(installedList);
      setUsage(stats.data.data?.content ?? []);

      // Stale inventory often has labels but no icons (pre-icon sync). Ask the child
      // to re-upload, same as the parent app Screen Time screen.
      if (installedList.length === 0 || !appsHaveIcons(installedList)) {
        setSyncingIcons(true);
        void deviceApi.sendCommand(deviceId, 'SYNC_INSTALLED_APPS').catch(() => undefined);
        for (let i = 0; i < 6; i++) {
          await sleep(2000);
          try {
            const refreshed = await monitoringApi.installedApps(deviceId);
            const next = refreshed.data.data ?? [];
            if (next.length > 0) {
              setApps(next);
            }
            if (appsHaveIcons(next)) {
              break;
            }
          } catch {
            // keep polling
          }
        }
        setSyncingIcons(false);
      }
    } catch {
      setError('Failed to load device');
      setSyncingIcons(false);
    }
  }, [deviceId]);

  useEffect(() => {
    void load();
  }, [load]);

  const appByPkg = useMemo(() => {
    const map = new Map<string, InstalledAppItem>();
    for (const app of apps) {
      map.set(app.packageName, app);
    }
    return map;
  }, [apps]);

  const merged: DeviceSummary | null = device
    ? {
        ...device,
        status: live?.status ?? device.status,
        batteryLevel: live?.batteryLevel ?? device.batteryLevel,
        charging: live?.charging ?? device.charging,
        networkType: live?.networkType ?? device.networkType,
        lastSeenAt: live?.lastSeenAt ?? device.lastSeenAt,
        lastLatitude: live?.latitude ?? device.lastLatitude,
        lastLongitude: live?.longitude ?? device.lastLongitude,
        foregroundApp: live?.foregroundApp ?? device.foregroundApp,
      }
    : null;

  const notifFeed = [
    ...liveNotifs.filter(n => n.deviceId === deviceId),
    ...notifications.filter(n => !liveNotifs.some(l => l.id === n.id)),
  ].slice(0, 30);

  const locFeed = liveLoc ? [liveLoc, ...locations.filter(l => l.id !== liveLoc.id)] : locations;

  const usageRows = useMemo(() => {
    const best = new Map<string, UsageStatsItem>();
    for (const entry of usage) {
      const prev = best.get(entry.packageName);
      if (!prev || (entry.usageMs ?? 0) > (prev.usageMs ?? 0)) {
        best.set(entry.packageName, entry);
      }
    }
    return [...best.values()]
      .sort((a, b) => (b.usageMs ?? 0) - (a.usageMs ?? 0))
      .slice(0, 20)
      .map((entry, index) => {
        const app = appByPkg.get(entry.packageName);
        const label = appDisplayName({
          appLabel: app?.appLabel,
          packageName: entry.packageName,
        });
        return {
          entry,
          label,
          iconBase64: app?.iconBase64,
          index,
        };
      });
  }, [usage, appByPkg]);

  const installedRows = useMemo(
    () =>
      [...apps]
        .sort((a, b) =>
          appDisplayName({appLabel: a.appLabel, packageName: a.packageName}).localeCompare(
            appDisplayName({appLabel: b.appLabel, packageName: b.packageName}),
          ),
        )
        .slice(0, 40)
        .map((app, index) => ({
          app,
          label: appDisplayName({appLabel: app.appLabel, packageName: app.packageName}),
          index,
        })),
    [apps],
  );

  const online = merged ? isDevicePresentOnline(merged.status, merged.lastSeenAt) : false;
  const revoked = (merged?.status ?? '').toUpperCase() === 'REVOKED';
  const foregroundLabel = merged?.foregroundApp
    ? appDisplayName({
        appLabel: appByPkg.get(merged.foregroundApp)?.appLabel,
        packageName: merged.foregroundApp,
      })
    : null;

  return (
    <div className="page">
      <Link to="/devices" className="back-link">
        ← #devices
      </Link>
      {error ? <div className="error">{error}</div> : null}
      {!merged ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="dc-msg detail-head">
            <div className={`dc-msg-avatar ${online ? 'online' : ''}`}>
              {deviceInitials(merged.deviceName)}
              <span className={`dc-status-dot ${revoked ? 'dnd' : online ? 'online' : 'idle'}`} />
            </div>
            <div className="dc-msg-body">
              <div className="dc-msg-head">
                <span className="dc-msg-author">{merged.deviceName}</span>
                <span className="dc-msg-time">
                  {merged.lastSeenAt ? new Date(merged.lastSeenAt).toLocaleString() : 'Never seen'}
                </span>
              </div>
              <div className="dc-msg-text">
                <span className={`status-pill ${revoked ? 'revoked' : online ? 'online' : 'offline'}`}>
                  {revoked ? 'Revoked' : online ? 'Online' : 'Offline'}
                </span>
                {merged.batteryLevel != null ? (
                  <span className="muted-inline"> · {merged.batteryLevel}%</span>
                ) : null}
                {merged.networkType ? (
                  <span className="muted-inline"> · {merged.networkType}</span>
                ) : null}
                {foregroundLabel ? (
                  <span className="muted-inline"> · {foregroundLabel}</span>
                ) : null}
              </div>
              <div className="btn-row wrap">
                <Link className="btn ghost" to={`/policies?deviceId=${merged.id}`}>
                  Policies
                </Link>
                <Link className="btn ghost" to={`/geofences?deviceId=${merged.id}`}>
                  Geofences
                </Link>
              </div>
            </div>
          </div>

          <LiveStreamPanel deviceId={merged.id} deviceName={merged.deviceName} />

          <div className="card">
            <h2>Status</h2>
            <dl className="meta grid-3">
              <div>
                <dt>Battery</dt>
                <dd>
                  {merged.batteryLevel != null ? `${merged.batteryLevel}%` : '—'}
                  {merged.charging ? ' · charging' : ''}
                </dd>
              </div>
              <div>
                <dt>Network</dt>
                <dd>{merged.networkType ?? '—'}</dd>
              </div>
              <div>
                <dt>Last seen</dt>
                <dd>{merged.lastSeenAt ? new Date(merged.lastSeenAt).toLocaleString() : '—'}</dd>
              </div>
              <div>
                <dt>Location</dt>
                <dd>
                  {merged.lastLatitude != null && merged.lastLongitude != null
                    ? `${merged.lastLatitude.toFixed(5)}, ${merged.lastLongitude.toFixed(5)}`
                    : '—'}
                </dd>
              </div>
            </dl>
          </div>

          <div className="split">
            <section className="card">
              <h2>Notifications</h2>
              {notifFeed.length === 0 ? (
                <p className="muted">No notifications yet.</p>
              ) : (
                <ul className="list">
                  {notifFeed.map((n, index) => {
                    const app = n.packageName ? appByPkg.get(n.packageName) : undefined;
                    const label = appDisplayName({
                      title: n.title,
                      appLabel: app?.appLabel,
                      packageName: n.packageName,
                      fallback: 'Notification',
                    });
                    return (
                      <li key={n.id} className="app-row">
                        <AppIcon
                          label={label}
                          iconBase64={app?.iconBase64}
                          index={index}
                          size={36}
                        />
                        <div className="app-row-body">
                          <strong>{label}</strong>
                          {n.body ? <div className="muted">{n.body}</div> : null}
                          <div className="tiny">{new Date(n.postedAt).toLocaleString()}</div>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>

            <section className="card">
              <h2>Location history</h2>
              {locFeed.length === 0 ? (
                <p className="muted">No location points yet.</p>
              ) : (
                <ul className="list">
                  {locFeed.map(p => (
                    <li key={p.id}>
                      <a
                        href={`https://www.openstreetmap.org/?mlat=${p.latitude}&mlon=${p.longitude}#map=16/${p.latitude}/${p.longitude}`}
                        target="_blank"
                        rel="noreferrer">
                        {p.latitude.toFixed(5)}, {p.longitude.toFixed(5)}
                      </a>
                      <div className="tiny">{new Date(p.recordedAt).toLocaleString()}</div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <div className="split">
            <section className="card">
              <h2>Installed apps</h2>
              {syncingIcons ? (
                <p className="muted">Refreshing app icons from the child phone…</p>
              ) : null}
              {installedRows.length === 0 ? (
                <p className="muted">No installed-app snapshot yet.</p>
              ) : (
                <ul className="list compact">
                  {installedRows.map(({app, label, index}) => (
                    <li key={app.id || app.packageName} className="app-row">
                      <AppIcon label={label} iconBase64={app.iconBase64} index={index} size={36} />
                      <div className="app-row-body">
                        <strong>{label}</strong>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="card">
              <h2>Recent usage</h2>
              {usageRows.length === 0 ? (
                <p className="muted">No usage stats yet.</p>
              ) : (
                <ul className="list compact">
                  {usageRows.map(({entry, label, iconBase64, index}) => (
                    <li key={entry.id} className="app-row">
                      <AppIcon label={label} iconBase64={iconBase64} index={index} size={36} />
                      <div className="app-row-body">
                        <div className="row-between">
                          <strong>{label}</strong>
                          <span className="tiny">{formatUsage(entry.usageMs ?? 0)}</span>
                        </div>
                        <div className="tiny">{new Date(entry.recordedAt).toLocaleString()}</div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}
