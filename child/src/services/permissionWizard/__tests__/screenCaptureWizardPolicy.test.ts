import {
  isScreenCaptureStepComplete,
  shouldAcceptScreenCaptureAfterConsent,
  shouldReturnToAccessibilityForScreenCapture,
} from '../screenCaptureWizardPolicy';

describe('isScreenCaptureStepComplete', () => {
  it('is complete when media projection is granted', () => {
    expect(isScreenCaptureStepComplete(true)).toBe(true);
  });

  it('is incomplete when media projection is not granted', () => {
    expect(isScreenCaptureStepComplete(false)).toBe(false);
  });
});

describe('shouldReturnToAccessibilityForScreenCapture', () => {
  it('does not bounce when media projection is already granted', () => {
    expect(
      shouldReturnToAccessibilityForScreenCapture({
        mediaProjectionGranted: true,
        accessibilityReady: false,
      }),
    ).toBe(false);
  });

  it('bounces when consent is missing and accessibility is not ready', () => {
    expect(
      shouldReturnToAccessibilityForScreenCapture({
        mediaProjectionGranted: false,
        accessibilityReady: false,
      }),
    ).toBe(true);
  });

  it('stays on screen capture when consent is missing but accessibility is ready', () => {
    expect(
      shouldReturnToAccessibilityForScreenCapture({
        mediaProjectionGranted: false,
        accessibilityReady: true,
      }),
    ).toBe(false);
  });
});

describe('shouldAcceptScreenCaptureAfterConsent', () => {
  it('accepts when projection is granted even if the grant flow was cancelled', () => {
    expect(
      shouldAcceptScreenCaptureAfterConsent({
        mediaProjectionGranted: true,
        flowCancelled: true,
      }),
    ).toBe(true);
  });

  it('rejects when cancelled and projection is still missing', () => {
    expect(
      shouldAcceptScreenCaptureAfterConsent({
        mediaProjectionGranted: false,
        flowCancelled: true,
      }),
    ).toBe(false);
  });

  it('continues when not cancelled and projection is still missing', () => {
    expect(
      shouldAcceptScreenCaptureAfterConsent({
        mediaProjectionGranted: false,
        flowCancelled: false,
      }),
    ).toBe(true);
  });
});
