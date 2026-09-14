import {useCallback, useEffect, useMemo, useState} from 'react';
import {Link, NavLink, Outlet, useLocation, useNavigate} from 'react-router-dom';
import {authApi, deviceApi} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {deviceInitials, isDevicePresentOnline} from '../lib/devicePresence';
import {isParentsTabEnabled} from '../lib/chatFriendsFeature';
import {useMediaQuery} from '../hooks/useMediaQuery';
import {MobileNavDrawer} from './MobileNavDrawer';
import {NavIcon} from './NavIcon';
import {DeviceDetailsPanel} from './DeviceDetailsPanel';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {disconnectWebSocket} from '../realtime/websocket';
import {LiveStreamHost} from '../livestream/LiveStreamHost';
import {useLiveStreamStore} from '../livestream/liveStreamStore';
import {GlobalChatCallHost} from '../realtime/GlobalChatCallHost';
import {useChatCallSessionStore} from '../realtime/chatCallSessionStore';
import {ParentWebAuthnSetup} from './ParentWebAuthnSetup';
import type {ChatCallParticipant, DeviceSummary} from '../types/api';

const EMPTY_CALL_PARTICIPANTS: ChatCallParticipant[] = [];

const NAV_GROUPS = [
  {
    label: 'Monitoring',
    items: [
      {to: '/devices', label: 'Devices', icon: 'devices'},
      {to: '/alerts', label: 'Alerts', icon: 'alerts'},
    ],
  },
  {
    label: 'Controls',
    items: [
      {to: '/policies', label: 'Policies', icon: 'policies'},
      {to: '/geofences', label: 'Geofences', icon: 'geofences'},
    ],
  },
  {
    label: 'Community',
    items: [
      {to: '/chat', label: 'Notes', icon: 'chat'},
      {to: '/parents', label: 'Parents', icon: 'parents'},
    ],
  },
  {
    label: 'Account',
    items: [
      {to: '/billing', label: 'Billing', icon: 'billing'},
      {to: '/audit', label: 'Audit', icon: 'audit'},
      {to: '/settings', label: 'Settings', icon: 'settings'},
    ],
  },
] as const;

function navGroupsForEnv() {
  const parentsEnabled = isParentsTabEnabled();
  return NAV_GROUPS.map(group => ({
    ...group,
    items: group.items.filter(item => parentsEnabled || item.to !== '/parents'),
  })).filter(group => group.items.length > 0);
}

const TITLES: Record<string, {title: string; subtitle: string}> = {
  '/devices': {
    title: 'Devices',
    subtitle: 'Paired child phones for this parent account',
  },
  '/alerts': {
    title: 'Alerts',
    subtitle: 'Policy violations, geofence events, and device alerts',
  },
  '/policies': {
    title: 'Policies',
    subtitle: 'App limits, schedules, and restrictions',
  },
  '/geofences': {
    title: 'Geofences',
    subtitle: 'Safe zones and location boundaries',
  },
  '/billing': {
    title: 'Billing',
    subtitle: 'Plan and subscription for this family',
  },
  '/audit': {
    title: 'Audit',
    subtitle: 'Security and account activity log',
  },
  '/settings': {
    title: 'Settings',
    subtitle: 'PIN, family handle, and account controls',
  },
  '/chat': {
    title: 'Notes',
    subtitle: 'Shared notebooks for parents — write together in real time',
  },
  '/parents': {
    title: 'Parents',
    subtitle: 'Shared notebooks — who is online and when they were last seen',
  },
};

function channelMeta(pathname: string) {
  if (pathname.startsWith('/devices/') && pathname !== '/devices') {
    return {title: 'Device', subtitle: 'Live status, stream, and activity for this phone', icon: 'devices'};
  }
  const base = TITLES[pathname] ?? {title: 'Parent Control', subtitle: 'Family monitoring'};
  return {
    ...base,
    icon: pathname.startsWith('/chat')
      ? 'chat'
      : pathname.startsWith('/parents')
        ? 'parents'
        : pathname.startsWith('/settings')
          ? 'settings'
          : 'devices',
  };
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

type NavChannelsProps = {
  pathname: string;
  onNavigate?: () => void;
};

function NavChannels({pathname, onNavigate}: NavChannelsProps) {
  const groups = useMemo(() => navGroupsForEnv(), []);
  return (
    <nav className="dc-channels">
      {groups.map(group => (
        <div key={group.label} className="dc-channel-group">
          <div className="dc-channel-cat">{group.label}</div>
          {group.items.map(item => {
            const nestedActive =
              item.to === '/devices' && pathname.startsWith('/devices/');
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/devices'}
                className={({isActive}) =>
                  `dc-channel${isActive || nestedActive ? ' active' : ''}`
                }
                onClick={onNavigate}>
                <span className="dc-channel-hash">
                  <NavIcon name={item.icon} />
                </span>
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </div>
      ))}
    </nav>
  );
}

type UserBarProps = {
  email: string | null;
  wsConnected: boolean;
  onSettingsNavigate?: () => void;
  onLogout: () => void;
};

function UserBar({email, wsConnected, onSettingsNavigate, onLogout}: UserBarProps) {
  return (
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
        <Link to="/settings" className="dc-icon-btn" title="Settings" onClick={onSettingsNavigate}>
          ⚙
        </Link>
        <button type="button" className="dc-icon-btn" title="Log out" onClick={onLogout}>
          ⎋
        </button>
      </div>
    </div>
  );
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
  const isMobile = useMediaQuery('(max-width: 639px)');
  const isTablet = useMediaQuery('(min-width: 640px) and (max-width: 1023px)');
  const [navDrawerOpen, setNavDrawerOpen] = useState(false);
  const meta = channelMeta(location.pathname);
  const isChatRoute = location.pathname === '/chat';
  const callJoined = useChatCallSessionStore(s => s.joined);
  const callRoomName = useChatCallSessionStore(s => s.roomName);
  const callChannelName = useChatCallSessionStore(s => s.channelName);
  const callParticipants = useChatCallSessionStore(
    s => s.call?.participants ?? EMPTY_CALL_PARTICIPANTS,
  );
  const setCallMinimized = useChatCallSessionStore(s => s.setMinimized);
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [clockLabel, setClockLabel] = useState(() =>
    new Date().toLocaleString(undefined, {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }),
  );

  useEffect(() => {
    setNavDrawerOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!isMobile) {
      setNavDrawerOpen(false);
    }
  }, [isMobile]);

  useEffect(() => {
    const id = window.setInterval(() => {
      setClockLabel(
        new Date().toLocaleString(undefined, {
          weekday: 'short',
          day: 'numeric',
          month: 'short',
          year: 'numeric',
          hour: 'numeric',
          minute: '2-digit',
        }),
      );
    }, 30_000);
    return () => window.clearInterval(id);
  }, []);

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

  const selectedDeviceId = useMemo(() => {
    const match = location.pathname.match(/^\/devices\/([^/]+)/);
    return match?.[1] ?? null;
  }, [location.pathname]);

  const focusDevice = useMemo(() => {
    if (selectedDeviceId) {
      return memberDevices.find(d => d.id === selectedDeviceId) ?? null;
    }
    if (location.pathname === '/devices' || location.pathname.startsWith('/devices')) {
      return online[0] ?? memberDevices[0] ?? null;
    }
    return online[0] ?? memberDevices[0] ?? null;
  }, [selectedDeviceId, location.pathname, online, memberDevices]);

  const onLogout = useCallback(async () => {
    if (refreshToken) {
      await authApi.logout(refreshToken);
    }
    disconnectWebSocket();
    useRealtimeStore.getState().reset();
    void useLiveStreamStore.getState().stop();
    useLiveStreamStore.getState().reset();
    clear();
    navigate('/login', {replace: true});
  }, [refreshToken, clear, navigate]);

  const closeNavDrawer = useCallback(() => setNavDrawerOpen(false), []);

  const handleLogoutClick = useCallback(() => {
    void onLogout();
  }, [onLogout]);

  return (
    <>
    <MobileNavDrawer
      open={isMobile && navDrawerOpen}
      pathname={location.pathname}
      email={email}
      wsConnected={wsConnected}
      onClose={closeNavDrawer}
      onLogout={handleLogoutClick}
    />
    <div className={`dc-app${isMobile ? ' dc-app--mobile' : ''}${isTablet ? ' dc-app--tablet' : ''}`}>
      {!isMobile ? (
      <aside className="dc-sidebar">
        <header className="dc-sidebar-header">
          <div className="dc-sidebar-brand">
            <img src={`${import.meta.env.BASE_URL}app-logo.png`} alt="" width={40} height={40} />
            <div className="dc-sidebar-brand-copy">
              <span className="dc-sidebar-title">Parent Control</span>
              <span className="dc-sidebar-tagline">Safe. Connected. Together.</span>
            </div>
          </div>
        </header>
        <div className="dc-family-pill" title="Family">
          FC
        </div>

        <NavChannels pathname={location.pathname} />
        <UserBar
          email={email}
          wsConnected={wsConnected}
          onLogout={handleLogoutClick}
        />
      </aside>
      ) : null}

      <div className="dc-main">
        <header className={`dc-topbar${isMobile ? ' dc-topbar--mobile' : ''}`}>
          {isMobile ? (
            <>
              <button
                type="button"
                className="dc-topbar-menu"
                aria-label="Open navigation"
                aria-expanded={navDrawerOpen}
                onClick={() => setNavDrawerOpen(true)}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                  <path d="M4 6h16M4 12h16M4 18h16" />
                </svg>
              </button>
              <div className="dc-topbar-mobile-center">
                <span className="dc-topbar-hash" aria-hidden>
                  <NavIcon name={meta.icon} />
                </span>
                <h1 className="dc-topbar-title">{meta.title}</h1>
              </div>
              <span className={`dc-live-pill ${wsConnected ? 'on' : 'off'}`}>
                <span className="dc-live-dot" />
                {wsConnected ? 'Live' : 'Offline'}
              </span>
            </>
          ) : (
            <>
              <div className="dc-topbar-left">
                <span className="dc-topbar-hash" aria-hidden>
                  <NavIcon name={meta.icon} />
                </span>
                <h1 className="dc-topbar-title">{meta.title}</h1>
                <span className="dc-topbar-divider" />
                <p className="dc-topbar-sub">{meta.subtitle}</p>
              </div>
              <div className="dc-topbar-right">
                <span className={`dc-live-pill ${wsConnected ? 'on' : 'off'}`}>
                  <span className="dc-live-dot" />
                  {wsConnected ? 'Live' : 'Offline'}
                </span>
                <span className="dc-topbar-icon" aria-hidden title="Search">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                    <circle cx="11" cy="11" r="7" />
                    <path d="M20 20l-3-3" />
                  </svg>
                </span>
                <span className="dc-topbar-icon" aria-hidden title="Notifications">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
                    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
                  </svg>
                </span>
                <span className="dc-topbar-clock">{clockLabel}</span>
              </div>
            </>
          )}
        </header>
        <main className={`dc-content${isChatRoute ? ' dc-content--chat' : ''}`}>
          <Outlet />
          <GlobalChatCallHost />
        </main>
      </div>

      {!isChatRoute && !isMobile && !isTablet ? (
      <aside className="dc-members" aria-label={callJoined ? 'In call' : 'Devices'}>
        {callJoined ? (
          <>
            <div className="dc-members-cat">In call</div>
            <p className="dc-members-empty" style={{marginBottom: 8}}>
              {callChannelName || callRoomName || 'Voice'}
            </p>
            {callParticipants.map(p => (
              <div key={p.userId} className="dc-member">
                <div className="dc-member-avatar online">
                  <span>
                    {(p.nickname || p.label || '?')
                      .split(/\s+/)
                      .map(w => w[0])
                      .join('')
                      .slice(0, 2)
                      .toUpperCase()}
                  </span>
                  <span className="dc-status-dot online" />
                </div>
                <div className="dc-member-meta">
                  <div className="dc-member-name">{p.nickname || p.label || 'Parent'}</div>
                  <div className="dc-member-activity">In call</div>
                </div>
              </div>
            ))}
            <button
              type="button"
              className="btn primary"
              style={{margin: '12px'}}
              onClick={() => {
                setCallMinimized(false);
                navigate('/chat');
              }}>
              Return to call
            </button>
          </>
        ) : focusDevice ? (
          <DeviceDetailsPanel
            device={focusDevice}
            nowMs={nowMs}
            onlineCount={online.length}
          />
        ) : (
          <>
            <div className="dc-members-cat">Online — {online.length}</div>
            <p className="dc-members-empty">No devices connected</p>
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
          </>
        )}
      </aside>
      ) : null}
    </div>
    <LiveStreamHost />
    <ParentWebAuthnSetup />
    </>
  );
}
