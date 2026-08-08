import {
  detectPairedDevice,
  detectRePairedDevice,
  isAdditionalDevicePairing,
  shouldDismissPairingCodeFromStatus,
  shouldKeepPairingCodeVisible,
} from '../services/pairingDetection';
import type {DeviceSummary} from '../services/api';

const device = (id: string, status = 'ONLINE'): DeviceSummary =>
  ({
    id,
    deviceName: `Device ${id}`,
    status,
  }) as DeviceSummary;

describe('detectPairedDevice', () => {
  it('finds a device id that was not in the baseline', () => {
    const baseline = new Set(['a']);
    expect(detectPairedDevice([device('a'), device('b')], baseline, Date.now())).toEqual(
      expect.objectContaining({id: 'b'}),
    );
  });

  it('returns undefined when every device was already in the baseline', () => {
    const baseline = new Set(['a']);
    expect(detectPairedDevice([device('a')], baseline, Date.now())).toBeUndefined();
  });

  it('waits a short grace period before accepting the first device with an empty baseline', () => {
    const started = Date.now();
    expect(detectPairedDevice([device('a')], new Set(), started)).toBeUndefined();
  });
});

describe('detectRePairedDevice', () => {
  it('detects revoked → online on a baseline device', () => {
    expect(
      detectRePairedDevice([device('a', 'ONLINE')], new Set(['a']), {a: 'REVOKED'}),
    ).toEqual(expect.objectContaining({id: 'a'}));
  });

  it('ignores devices that stayed online', () => {
    expect(
      detectRePairedDevice([device('a', 'ONLINE')], new Set(['a']), {a: 'ONLINE'}),
    ).toBeUndefined();
  });
});

describe('shouldDismissPairingCodeFromStatus', () => {
  it('dismisses when the code was claimed even without a deviceId', () => {
    expect(shouldDismissPairingCodeFromStatus({used: true})).toBe(true);
  });

  it('dismisses when the code expired', () => {
    expect(shouldDismissPairingCodeFromStatus({used: false, expired: true})).toBe(true);
  });

  it('keeps the code while unused and unexpired', () => {
    expect(shouldDismissPairingCodeFromStatus({used: false, expired: false})).toBe(false);
  });
});

describe('shouldKeepPairingCodeVisible', () => {
  it('keeps Parent B waiting until the code is claimed', () => {
    expect(
      shouldKeepPairingCodeVisible('ABC123', {used: false, expired: false}),
    ).toBe(true);
  });

  it('hides after claim', () => {
    expect(shouldKeepPairingCodeVisible('ABC123', {used: true})).toBe(false);
  });

  it('is false when no pending code', () => {
    expect(shouldKeepPairingCodeVisible(null, {used: false})).toBe(false);
  });
});

describe('isAdditionalDevicePairing', () => {
  it('is true when baseline already had devices', () => {
    expect(isAdditionalDevicePairing(1)).toBe(true);
    expect(isAdditionalDevicePairing(0)).toBe(false);
  });
});
