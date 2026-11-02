import {describe, expect, it} from 'vitest';
import type {DeviceSummary} from '../types/api';
import {isRevokedDeviceStatus, revocableDevices} from './removePairing';

function device(partial: Partial<DeviceSummary> & Pick<DeviceSummary, 'id' | 'deviceName' | 'status'>): DeviceSummary {
  return partial;
}

describe('isRevokedDeviceStatus', () => {
  it('detects REVOKED case-insensitively', () => {
    expect(isRevokedDeviceStatus('REVOKED')).toBe(true);
    expect(isRevokedDeviceStatus('revoked')).toBe(true);
    expect(isRevokedDeviceStatus('ONLINE')).toBe(false);
    expect(isRevokedDeviceStatus(null)).toBe(false);
  });
});

describe('revocableDevices', () => {
  it('returns only non-revoked pairings', () => {
    const list = revocableDevices([
      device({id: '1', deviceName: 'Phone A', status: 'ONLINE'}),
      device({id: '2', deviceName: 'Phone B', status: 'REVOKED'}),
      device({id: '3', deviceName: 'Phone C', status: 'OFFLINE'}),
    ]);
    expect(list.map(d => d.id)).toEqual(['1', '3']);
  });
});
