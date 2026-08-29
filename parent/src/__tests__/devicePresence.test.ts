import {
  buildStaleReadinessMessage,
  formatLastHeardAgo,
  hasAgedReadinessSnapshot,
  hasStaleGrantedReadiness,
  isDevicePresentOnline,
  PRESENCE_STALE_MS,
} from '../utils/devicePresence';

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

describe('formatLastHeardAgo', () => {
  const now = Date.parse('2026-07-26T14:40:00.000Z');

  it('formats minutes ago', () => {
    expect(formatLastHeardAgo('2026-07-26T14:35:00.000Z', now)).toBe('5 min ago');
  });

  it('returns null for invalid input', () => {
    expect(formatLastHeardAgo(null, now)).toBeNull();
  });
});

describe('hasStaleGrantedReadiness', () => {
  it('is true when offline but another row is granted', () => {
    expect(
      hasStaleGrantedReadiness(false, [
        {key: 'online', ok: false},
        {key: 'camera', ok: true},
      ]),
    ).toBe(true);
  });

  it('is false when online', () => {
    expect(
      hasStaleGrantedReadiness(true, [
        {key: 'online', ok: true},
        {key: 'camera', ok: true},
      ]),
    ).toBe(false);
  });
});

describe('hasAgedReadinessSnapshot', () => {
  const now = Date.parse('2026-07-26T14:40:00.000Z');

  it('is true when online and lastSeen is older than one heartbeat cycle', () => {
    const lastSeen = new Date(now - 7 * 60 * 1000).toISOString();
    expect(
      hasAgedReadinessSnapshot(true, lastSeen, [
        {key: 'online', ok: true},
        {key: 'a11y', ok: true},
      ], now),
    ).toBe(true);
  });

  it('is false when lastSeen is fresh', () => {
    const lastSeen = new Date(now - 2 * 60 * 1000).toISOString();
    expect(
      hasAgedReadinessSnapshot(true, lastSeen, [{key: 'a11y', ok: true}], now),
    ).toBe(false);
  });
});

describe('buildStaleReadinessMessage', () => {
  const now = Date.parse('2026-07-26T14:40:00.000Z');

  it('includes last heard age when available', () => {
    expect(buildStaleReadinessMessage('2026-07-26T14:30:00.000Z', now)).toContain('10 min ago');
  });
});
