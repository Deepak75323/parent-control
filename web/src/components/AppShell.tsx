import {useCallback, useEffect, useMemo, useState} from 'react';
import {Link, NavLink, Outlet, useLocation, useNavigate} from 'react-router-dom';
import {authApi, deviceApi} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {deviceInitials, isDevicePresentOnline} from '../lib/devicePresence';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {disconnectWebSocket} from '../realtime/websocket';
import type {DeviceSummary} from '../types/api';

const NAV_GROUPS = [
  {
    label: 'Monitoring',
    items: [
      {to: '/devices', label: 'devices', icon: '#'},
      {to: '/alerts', label: 'alerts', icon: '#'},
    ],
  },
  {
    label: 'Controls',
    items: [
      {to: '/policies', label: 'policies', icon: '#'},
      {to: '/geofences', label: 'geofences', icon: '#'},
    ],
  },
  {
    label: 'Account',
    items: [
      {to: '/billing', label: 'billing', icon: '#'},
      {to: '/audit', label: 'audit', icon: '#'},
      {to: '/settings', label: 'settings', icon: '⚙'},
    ],
  },
] as const;

const TITLES: Record<string, {title: string; subtitle: string}> = {
  '/devices': {
    title: 'devices',
    subtitle: 'Paired child phones for this parent account',
  },
  '/alerts': {
    title: 'alerts',
    subtitle: 'Policy violations, geofence events, and device alerts',
  },
  '/policies': {
    title: 'policies',
    subtitle: 'App limits, schedules, and restrictions',
  },
  '/geofences': {
    title: 'geofences',
    subtitle: 'Safe zones and location boundaries',
  },
  '/billing': {
    title: 'billing',
    subtitle: 'Plan and subscription for this family',
  },
  '/audit': {
    title: 'audit',
    subtitle: 'Security and account activity log',
  },
  '/settings': {
    title: 'settings',
    subtitle: 'PIN, family handle, and account controls',
  },
};

function channelMeta(pathname: string) {
  if (pathname.startsWith('/devices/') && pathname !== '/devices') {
    return {title: 'device', subtitle: 'Live status, stream, and activity for this phone'};
  }
  return TITLES[pathname] ?? {title: 'parent-control', subtitle: 'Family monitoring'};
}

function mergeLive(device: DeviceSummary): DeviceSummary {
  const live = useRealtimeStore.getState().deviceStatuses[device.id];
  if (!live) {
    return device;
  }
  return {
    ...device,
    status: live.status ?? device.status,
    batteryLevel: live.batteryLevel ?? device.batteryLevel,
    lastSeenAt: live.lastSeenAt ?? device.lastSeenAt,
    foregroundApp: live.foregroundApp ?? device.foregroundApp,
  };
}

export function AppShell() {
  const email = useAuthStore(s => s.email);
  const refreshToken = useAuthStore(s => s.refreshToken);
  const clear = useAuthStore(s => s.clear);
  const wsConnected = useRealtimeStore(s => s.wsConnected);
  const deviceStatuses = useRealtimeStore(s => s.deviceStatuses);
  const deviceListEpoch = useRealtimeStore(s => s.deviceListEpoch);
  const removedDeviceIds = useRealtimeStore(s => s.removedDeviceIds);
  const navigate = useNavigate();
  const location = useLocation();
  const meta = channelMeta(location.pathname);
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [nowMs, setNowMs] = useState(() => Date.now());

  const loadDevices = useCallback(async () => {
    try {
      const {data} = await deviceApi.list();
      setDevices(data.data ?? []);
    } catch {
      // sidebar is best-effort
    }
  }, []);

  useEffect(() => {
    void loadDevices();
  }, [loadDevices, deviceListEpoch]);

  useEffect(() => {
    const id = window.setInterval(() => setNowMs(Date.now()), 15_000);
    return () => window.clearInterval(id);
  }, []);

  const memberDevices = useMemo(() => {
    void deviceStatuses;
    return devices
      .filter(d => !removedDeviceIds[d.id])
      .map(mergeLive)
      .filter(d => (d.status ?? '').toUpperCase() !== 'REVOKED');
  }, [devices, removedDeviceIds, deviceStatuses]);

  const online = memberDevices.filter(d =>
    isDevicePresentOnline(d.status, d.lastSeenAt, nowMs),
  );
  const offline = memberDevices.filter(
    d => !isDevicePresentOnline(d.status, d.lastSeenAt, nowMs),
  );

  const onLogout = async () => {
    if (refreshToken) {
      await authApi.logout(refreshToken);
    }
    disconnectWebSocket();
    useRealtimeStore.getState().reset();
    clear();
    navigate('/login', {replace: true});
  };

  return (
    <div className="dc-app">
      <aside className="dc-rail" aria-label="Workspaces">
        <Link to="/devices" className="dc-rail-pill active" title="Parent Control">
          <img src={`${import.meta.env.BASE_URL}app-logo.png`} alt="" width={48} height={48} />
        </Link>
        <div className="dc-rail-sep" />
        <div className="dc-rail-pill muted" title="Family">
          <span>FC</span>
        </div>
      </aside>

      <aside className="dc-sidebar">
        <header className="dc-sidebar-header">
          <span className="dc-sidebar-title">Parent Control</span>
          <span className="dc-sidebar-chevron" aria-hidden>
            ▾
          </span>
        </header>

        <nav className="dc-channels">
          {NAV_GROUPS.map(group => (
            <div key={group.label} className="dc-channel-group">
              <div className="dc-channel-cat">{group.label}</div>
              {group.items.map(item => {
                const nestedActive =
                  item.to === '/devices' && location.pathname.startsWith('/devices/');
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === '/devices'}
                    className={({isActive}) =>
                      `dc-channel${isActive || nestedActive ? ' active' : ''}`
                    }>
                    <span className="dc-channel-hash">{item.icon}</span>
                    <span>{item.label}</span>
                  </NavLink>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="dc-userbar">
          <div className="dc-user-avatar">
            <span>{(email ?? 'P').slice(0, 1).toUpperCase()}</span>
            <span className={`dc-status-dot ${wsConnected ? 'online' : 'idle'}`} />
          </div>
          <div className="dc-user-meta">
            <div className="dc-user-name">{email?.split('@')[0] ?? 'Parent'}</div>
            <div className="dc-user-status">{wsConnected ? 'Live sync' : 'Connecting…'}</div>
          </div>
          <div className="dc-user-actions">
            <Link to="/settings" className="dc-icon-btn" title="Settings">
              ⚙
            </Link>
            <button type="button" className="dc-icon-btn" title="Log out" onClick={() => void onLogout()}>
              ⎋
            </button>
          </div>
        </div>
      </aside>

      <div className="dc-main">
        <header className="dc-topbar">
          <div className="dc-topbar-left">
            <span className="dc-topbar-hash">#</span>
            <h1 className="dc-topbar-title">{meta.title}</h1>
            <span className="dc-topbar-divider" />
            <p className="dc-topbar-sub">{meta.subtitle}</p>
          </div>
          <div className="dc-topbar-right">
            <span className={`dc-live-pill ${wsConnected ? 'on' : 'off'}`}>
              <span className="dc-live-dot" />
              {wsConnected ? 'Live' : 'Offline'}
            </span>
          </div>
        </header>
        <main className="dc-content">
          <Outlet />
        </main>
      </div>

      <aside className="dc-members" aria-label="Devices">
        <div className="dc-members-cat">Online — {online.length}</div>
        {online.length === 0 ? (
          <p className="dc-members-empty">No devices online</p>
        ) : (
          online.map(d => (
            <Link key={d.id} to={`/devices/${d.id}`} className="dc-member">
              <div className="dc-member-avatar online">
                <span>{deviceInitials(d.deviceName)}</span>
                <span className="dc-status-dot online" />
              </div>
              <div className="dc-member-meta">
                <div className="dc-member-name">{d.deviceName}</div>
                <div className="dc-member-activity">
                  {d.foregroundApp?.split('.').pop() ??
                    (d.batteryLevel != null ? `${d.batteryLevel}% battery` : 'Online')}
                </div>
              </div>
            </Link>
          ))
        )}

        {offline.length > 0 ? (
          <>
            <div className="dc-members-cat spaced">Offline — {offline.length}</div>
            {offline.map(d => (
              <Link key={d.id} to={`/devices/${d.id}`} className="dc-member soft">
                <div className="dc-member-avatar">
                  <span>{deviceInitials(d.deviceName)}</span>
                  <span className="dc-status-dot idle" />
                </div>
                <div className="dc-member-meta">
                  <div className="dc-member-name">{d.deviceName}</div>
                  <div className="dc-member-activity">Offline</div>
                </div>
              </Link>
            ))}
          </>
        ) : null}
      </aside>
    </div>
  );
}
