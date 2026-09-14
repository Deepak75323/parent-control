import {Link} from 'react-router-dom';
import {deviceInitials, isDevicePresentOnline} from '../lib/devicePresence';
import {appDisplayName} from '../lib/appDisplayName';
import type {DeviceSummary} from '../types/api';

type Props = {
  device: DeviceSummary;
  nowMs: number;
  onlineCount: number;
};

export function DeviceDetailsPanel({device, nowMs, onlineCount}: Props) {
  const online = isDevicePresentOnline(device.status, device.lastSeenAt, nowMs);
  const revoked = (device.status ?? '').toUpperCase() === 'REVOKED';
  const appLabel = device.foregroundApp
    ? appDisplayName({packageName: device.foregroundApp})
    : null;
  const deviceIdShort =
    device.deviceName?.replace(/\s+/g, '').slice(-10) || device.id.slice(0, 8);

  return (
    <div className="device-details-panel">
      <div className="dc-members-cat">
        {online ? 'Online' : 'Offline'} — {onlineCount}
      </div>

      <div className="device-details-hero">
        <div className={`device-details-avatar ${online ? 'online' : ''}`}>
          {deviceInitials(device.deviceName)}
          <span className={`dc-status-dot ${revoked ? 'dnd' : online ? 'online' : 'idle'}`} />
        </div>
        <div className="device-details-hero-meta">
          <div className="device-details-name">{device.deviceName}</div>
          <div className="device-details-sub">
            {revoked ? 'Revoked' : online ? 'Online' : 'Offline'}
            {device.batteryLevel != null && !revoked ? ` · ${device.batteryLevel}% battery` : ''}
          </div>
        </div>
      </div>

      <div className="device-details-tabs" role="tablist" aria-label="Device panel">
        <span className="device-details-tab active" role="tab" aria-selected="true">
          Overview
        </span>
        <Link className="device-details-tab" to={`/devices/${device.id}`} role="tab">
          Settings
        </Link>
        <Link className="device-details-tab" to={`/devices/${device.id}`} role="tab">
          Activity
        </Link>
      </div>

      <div className="device-details-card">
        <div className="device-details-phone" aria-hidden>
          <div className="device-details-phone-screen" />
        </div>
        <div className="device-details-title-row">
          <div>
            <div className="device-details-card-title">{device.deviceName}</div>
            <div className="device-details-card-sub">Android Device</div>
          </div>
          <span className={`status-pill ${revoked ? 'revoked' : online ? 'online' : 'offline'}`}>
            {revoked ? 'Revoked' : online ? 'Online' : 'Offline'}
          </span>
        </div>

        <dl className="device-details-grid">
          <div>
            <dt>Battery</dt>
            <dd>
              {device.batteryLevel != null ? `${device.batteryLevel}%` : '—'}
              {device.charging ? ' · charging' : ''}
            </dd>
          </div>
          <div>
            <dt>Network</dt>
            <dd>{device.networkType ?? '—'}</dd>
          </div>
          <div>
            <dt>Connection</dt>
            <dd>{appLabel ?? (online ? 'Connected' : 'Idle')}</dd>
          </div>
          <div>
            <dt>Last synced</dt>
            <dd>{device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : 'Never'}</dd>
          </div>
          <div>
            <dt>Device ID</dt>
            <dd>{deviceIdShort}</dd>
          </div>
        </dl>
      </div>

      <div className="device-details-actions">
        <Link className="device-action-btn" to={`/geofences?deviceId=${device.id}`}>
          Locate device
        </Link>
        <Link className="device-action-btn" to={`/devices/${device.id}`}>
          View usage
        </Link>
        <Link className="device-action-btn" to={`/policies?deviceId=${device.id}`}>
          Apply policy
        </Link>
        <Link className="device-action-btn" to={`/devices/${device.id}`}>
          Open device
        </Link>
      </div>
    </div>
  );
}
