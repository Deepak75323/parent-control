/**
 * Wizard policy for the screen-capture step.
 *
 * Media projection consent is the step’s completion signal. Accessibility is only
 * a prerequisite for *obtaining* consent (auto-approve), not for counting the
 * step as done once a live grant exists.
 */

export function isScreenCaptureStepComplete(mediaProjectionGranted: boolean): boolean {
  return mediaProjectionGranted;
}

/**
 * Bounce back to the accessibility step only when consent is still missing and
 * Clock Time accessibility is not ready to auto-approve the system dialog.
 */
export function shouldReturnToAccessibilityForScreenCapture(options: {
  mediaProjectionGranted: boolean;
  accessibilityReady: boolean;
}): boolean {
  if (options.mediaProjectionGranted) {
    return false;
  }
  return !options.accessibilityReady;
}

/**
 * After the system consent activity returns, a live grant wins even if the JS
 * grant "generation" was cancelled by AppState blur during the dialog.
 */
export function shouldAcceptScreenCaptureAfterConsent(options: {
  mediaProjectionGranted: boolean;
  flowCancelled: boolean;
}): boolean {
  if (options.mediaProjectionGranted) {
    return true;
  }
  return !options.flowCancelled;
}
