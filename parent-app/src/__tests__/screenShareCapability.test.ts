import {screenShareBlockedMessage} from '../screenShareCapability';

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
});
