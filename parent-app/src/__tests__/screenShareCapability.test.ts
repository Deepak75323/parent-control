import {
  isScreenShareCapable,
  isScreenShareCapableMerged,
  screenShareBlockedMessage,
} from '../utils/screenShareCapability';

describe('screenShareBlockedMessage', () => {
  it('returns reconsent message for LOST_AFTER_PROCESS_DEATH', () => {
    expect(
      screenShareBlockedMessage({
        mediaProjectionGranted: false,
        mediaProjectionConsentState: 'LOST_AFTER_PROCESS_DEATH',
      }),
    ).toContain('re-approve');
  });

  it('returns recovering message for RECOVERY_PENDING', () => {
    expect(
      screenShareBlockedMessage({
        mediaProjectionGranted: false,
        mediaProjectionConsentState: 'RECOVERY_PENDING',
      }),
    ).toContain('restoring');
  });

  it('returns empty when capable', () => {
    expect(
      screenShareBlockedMessage({
        mediaProjectionGranted: true,
        mediaProjectionConsentState: 'GRANTED_ACTIVE',
      }),
    ).toBe('');
  });

  it('enables screen share when API says GRANTED_ACTIVE but live WS is stale RECOVERY_PENDING', () => {
    expect(
      isScreenShareCapableMerged(
        {mediaProjectionGranted: false, mediaProjectionConsentState: 'RECOVERY_PENDING'},
        {mediaProjectionGranted: true, mediaProjectionConsentState: 'GRANTED_ACTIVE'},
      ),
    ).toBe(true);
  });

  it('blocks stale granted=true when consent state is LOST_AFTER_PROCESS_DEATH', () => {
    expect(
      isScreenShareCapable({
        mediaProjectionGranted: true,
        mediaProjectionConsentState: 'LOST_AFTER_PROCESS_DEATH',
      }),
    ).toBe(false);
    expect(
      screenShareBlockedMessage({
        mediaProjectionGranted: true,
        mediaProjectionConsentState: 'LOST_AFTER_PROCESS_DEATH',
      }),
    ).toContain('re-approve');
  });
});
