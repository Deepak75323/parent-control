import type {DeviceSummary} from '../types/api';

export function isRevokedDeviceStatus(status?: string | null): boolean {
  return (status ?? '').toUpperCase() === 'REVOKED';
}

/** Active pairings that can still be removed. */
export function revocableDevices(devices: DeviceSummary[]): DeviceSummary[] {
  return devices.filter(d => !isRevokedDeviceStatus(d.status));
}
