import {describe, expect, it} from 'vitest';
import {
  formatLastSeenAgo,
  formatLastSeenClockLabel,
  formatPartnerPresenceLabel,
} from './parentPresence';

describe('formatLastSeenAgo', () => {
  const now = Date.parse('2026-09-14T12:00:00Z');

  it('formats minutes', () => {
    expect(formatLastSeenAgo('2026-09-14T11:50:00Z', now)).toBe('10 min ago');
  });

  it('formats just now', () => {
    expect(formatLastSeenAgo('2026-09-14T11:59:30Z', now)).toBe('just now');
  });
});

describe('formatPartnerPresenceLabel', () => {
  const now = Date.parse('2026-09-14T12:00:00Z');

  it('shows Online when online', () => {
    expect(formatPartnerPresenceLabel({online: true, hidden: false}, now)).toBe('Online');
  });

  it('shows Last seen when offline', () => {
    expect(
      formatPartnerPresenceLabel(
        {online: false, lastSeenAt: '2026-09-14T11:50:00Z', hidden: false},
        now,
      ),
    ).toBe('Last seen 10 min ago');
  });

  it('returns null when hidden', () => {
    expect(
      formatPartnerPresenceLabel({online: true, hidden: true}, now),
    ).toBeNull();
  });
});

describe('formatLastSeenClockLabel', () => {
  const now = Date.parse('2026-09-14T12:00:00');

  it('formats today', () => {
    expect(formatLastSeenClockLabel('2026-09-14T10:45:00', now)).toMatch(/^Today,/);
  });

  it('formats yesterday', () => {
    expect(formatLastSeenClockLabel('2026-09-13T22:10:00', now)).toMatch(/^Yesterday,/);
  });

  it('returns null without timestamp', () => {
    expect(formatLastSeenClockLabel(null, now)).toBeNull();
  });
});
