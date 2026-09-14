import {useEffect, useRef} from 'react';
import {createPortal} from 'react-dom';
import {useNavigate} from 'react-router-dom';
import {lockBodyScroll} from '../lib/scrollLock';
import {NavIcon} from './NavIcon';

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

type Props = {
  open: boolean;
  pathname: string;
  email: string | null;
  wsConnected: boolean;
  onClose: () => void;
  onLogout: () => void;
};

function isItemActive(pathname: string, to: string): boolean {
  if (to === '/devices') {
    return pathname === '/devices' || pathname.startsWith('/devices/');
  }
  return pathname === to;
}

export function MobileNavDrawer({
  open,
  pathname,
  email,
  wsConnected,
  onClose,
  onLogout,
}: Props) {
  const navigate = useNavigate();
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    return lockBodyScroll();
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    closeButtonRef.current?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open || typeof document === 'undefined') {
    return null;
  }

  const goTo = (to: string) => {
    onClose();
    const alreadyThere = pathname === to || (to === '/devices' && pathname.startsWith('/devices/'));
    if (!alreadyThere) {
      window.requestAnimationFrame(() => {
        window.requestAnimationFrame(() => navigate(to));
      });
    }
  };

  return createPortal(
    <div className="dc-mobile-overlay" role="dialog" aria-modal="true" aria-label="Navigation">
      <aside className="dc-mobile-overlay-drawer">
        <header className="dc-sidebar-header">
          <div className="dc-sidebar-brand">
            <img src={`${import.meta.env.BASE_URL}app-logo.png`} alt="" width={36} height={36} />
            <div className="dc-sidebar-brand-copy">
              <span className="dc-sidebar-title">Parent Control</span>
              <span className="dc-sidebar-tagline">Safe. Connected. Together.</span>
            </div>
          </div>
          <button
            ref={closeButtonRef}
            type="button"
            className="dc-mobile-drawer-close"
            aria-label="Close navigation"
            onClick={onClose}>
            ×
          </button>
        </header>
        <div className="dc-family-pill" title="Family">
          FC
        </div>
        <nav className="dc-channels">
          {NAV_GROUPS.map(group => (
            <div key={group.label} className="dc-channel-group">
              <div className="dc-channel-cat">{group.label}</div>
              {group.items.map(item => (
                <button
                  key={item.to}
                  type="button"
                  className={`dc-channel dc-channel-btn${
                    isItemActive(pathname, item.to) ? ' active' : ''
                  }`}
                  onClick={() => goTo(item.to)}>
                  <span className="dc-channel-hash">
                    <NavIcon name={item.icon} />
                  </span>
                  <span>{item.label}</span>
                </button>
              ))}
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
            <button
              type="button"
              className="dc-icon-btn"
              title="Settings"
              aria-label="Settings"
              onClick={() => goTo('/settings')}>
              ⚙
            </button>
            <button type="button" className="dc-icon-btn" title="Log out" onClick={onLogout}>
              ⎋
            </button>
          </div>
        </div>
      </aside>
      <button
        type="button"
        className="dc-mobile-overlay-backdrop"
        aria-label="Close navigation"
        onClick={onClose}
      />
    </div>,
    document.body,
  );
}
