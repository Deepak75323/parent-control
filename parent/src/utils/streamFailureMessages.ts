/** Parent-friendly text for child-reported stream failure codes. */
export function streamFailureMessage(reason?: string | null): string {
  switch (reason) {
    case 'PROJECTION_LOST_AFTER_RESTART':
      return (
        'Screen capture permission was lost after the child phone restarted. ' +
        'The child must re-approve screen sharing — open Permissions on the child phone and tap Grant screen capture, then try again.'
      );
    case 'PROJECTION_REQUIRES_RECONSENT':
      return (
        'The child must re-approve screen sharing. ' +
        'Open the child app → Permissions → Grant screen capture, then try again.'
      );
    case 'PROJECTION_RECOVERY_PENDING':
      return (
        'The child app is restoring screen capture permission. Wait a moment — do not keep retrying.'
      );
    case 'PROJECTION_RECOVERY_FAILED':
      return (
        'Screen capture could not be restored automatically on the child phone. ' +
        'Open Permissions on the child phone and tap Grant screen capture, then try again.'
      );
    case 'NO_SCREEN_CAPTURE_CONSENT':
      return (
        'Screen capture was never set up on the child phone. ' +
        'Open the child app → Permissions → Grant screen capture.'
      );
    case 'PROJECTION_SESSION_NOT_WARM':
      return (
        'Screen capture is not ready yet. Open the child app once, wait a few seconds, then try again.'
      );
    case 'FGS_NOT_READY':
    case 'SCREEN_FGS_FAILED':
      return (
        'Screen capture service did not start on the child phone. Open the child app briefly, then try again.'
      );
    case 'AUDIO_FGS_FAILED':
    case 'FGS_GATE_CLOSED':
      return (
        'Microphone service did not start on the child phone. Open the child app briefly, then try again.'
      );
    case 'CAMERA_FGS_FAILED':
      return (
        'Camera service did not start on the child phone. Open the child app briefly, then try again.'
      );
    case 'AUDIO_FOCUS_DENIED':
      return (
        'Another app is using the microphone on the child phone. Close other apps and try again.'
      );
    case 'CAPTURE_BUSY':
      return 'A stream is already running on the child phone. Stop it and try again.';
    default:
      if (reason?.startsWith('FGS_TIMEOUT_')) {
        // Android 15+ enforces a time limit on typed foreground services.
        return (
          'Android ended this monitoring session to save battery. ' +
          'Tap Reconnect to start a new session.'
        );
      }
      if (reason?.startsWith('ICE_')) {
        return 'Connection failed between phones. Check Wi‑Fi or mobile data on both devices and tap Reconnect.';
      }
      return reason
        ? `Stream failed (${reason}). Try again in a moment.`
        : 'Stream failed. Try again in a moment.';
  }
}
