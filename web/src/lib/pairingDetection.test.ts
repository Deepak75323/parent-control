import {describe, expect, it} from 'vitest';
import {
  detectPairedDevice,
  detectRePairedDevice,
  shouldDismissPairingCodeFromStatus,
  shouldDismissPairingSession,
} from './pairingDetection';

const device = (id: string, status = 'ONLINE') => ({id, deviceName: `Device ${id}`, status});

describe('detectPairedDevice', () => {
  it('finds a device id that was not in the baseline', () => {
    expect(detectPairedDevice([device('a'), device('b')], new Set(['a']), Date.now())).toEqual(
      expect.objectContaining({id: 'b'}),
    );
  });

  it('returns undefined when every device was already in the baseline', () => {
    expect(detectPairedDevice([device('a')], new Set(['a']), Date.now())).toBeUndefined();
  });
});

describe('detectRePairedDevice', () => {
  it('detects revoked → online on a baseline device', () => {
    expect(
      detectRePairedDevice([device('a', 'ONLINE')], new Set(['a']), {a: 'REVOKED'}),
    ).toEqual(expect.objectContaining({id: 'a'}));
  });
});

describe('shouldDismissPairingSession', () => {
  it('dismisses when the backend marks the code used', () => {
    expect(
      shouldDismissPairingSession({used: true}, [], new Set(), {}, Date.now()),
    ).toBe(true);
  });

  it('dismisses when a new device appears in the list', () => {
    expect(
      shouldDismissPairingSession(
        {used: false, expired: false},
        [device('new')],
        new Set(['old']),
        {old: 'ONLINE'},
        Date.now() - 5000,
      ),
    ).toBe(true);
  });

  it('keeps waiting while the code is unused and no new device exists', () => {
    expect(
      shouldDismissPairingSession(
        {used: false, expired: false},
        [device('a')],
        new Set(['a']),
        {a: 'ONLINE'},
        Date.now(),
      ),
    ).toBe(false);
  });
});

describe('shouldDismissPairingCodeFromStatus', () => {
  it('dismisses when the code expired', () => {
    expect(shouldDismissPairingCodeFromStatus({used: false, expired: true})).toBe(true);
  });
});
