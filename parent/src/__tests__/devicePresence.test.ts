import {isDevicePresentOnline, PRESENCE_STALE_MS} from '../utils/devicePresence';

describe('isDevicePresentOnline', () => {
  const now = Date.parse('2026-07-26T14:40:00.000Z');

  it('returns false for REVOKED even with fresh lastSeen', () => {
    expect(isDevicePresentOnline('REVOKED', '2026-07-26T14:39:00.000Z', now)).toBe(false);
  });

  it('returns true for fresh ONLINE lastSeen', () => {
    expect(isDevicePresentOnline('ONLINE', '2026-07-26T14:39:30.000Z', now)).toBe(true);
  });

  it('returns true for ONLINE with no lastSeen', () => {
    expect(isDevicePresentOnline('ONLINE', null, now)).toBe(true);
  });

  it('returns true within the heartbeat window', () => {
    const withinCycle = new Date(now - 5 * 60 * 1000).toISOString();
    expect(isDevicePresentOnline('ONLINE', withinCycle, now)).toBe(true);
  });

  it('returns false when ONLINE lastSeen is older than stale window', () => {
    const stale = new Date(now - PRESENCE_STALE_MS - 1000).toISOString();
    expect(isDevicePresentOnline('ONLINE', stale, now)).toBe(false);
  });

  it('returns true for OFFLINE when lastSeen is still fresh (status lag)', () => {
    expect(isDevicePresentOnline('OFFLINE', '2026-07-26T14:38:00.000Z', now)).toBe(true);
  });

  it('returns false for OFFLINE when lastSeen is stale', () => {
    const stale = new Date(now - PRESENCE_STALE_MS - 1000).toISOString();
    expect(isDevicePresentOnline('OFFLINE', stale, now)).toBe(false);
  });

  it('returns false for OFFLINE with no lastSeen', () => {
    expect(isDevicePresentOnline('OFFLINE', null, now)).toBe(false);
  });
});
