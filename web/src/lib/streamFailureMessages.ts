/** Parent-facing text for child-reported stream failure codes. */

function formatCameraOccupantHint(rawHint: string): string | null {
  const hint = rawHint.trim();
  if (!hint) {
    return null;
  }
  if (hint.includes('.') && hint === hint.toLowerCase()) {
    const last = hint.split('.').filter(Boolean).pop() ?? hint;
    return last.charAt(0).toUpperCase() + last.slice(1);
  }
  return hint.replace(/_/g, ' ');
}

function cameraInUseMessage(reason: string): string {
  const colon = reason.indexOf(':');
  const hint = colon >= 0 ? reason.slice(colon + 1) : '';
  const appName = formatCameraOccupantHint(hint);
  if (appName) {
    return (
      `${appName} appears to be using the camera on the child phone. ` +
      'Close that app or end the call, then tap Reconnect.'
    );
  }
  return (
    'Another app is using the camera on the child phone (for example a video call). ' +
    'End the call or close that app, then tap Reconnect.'
  );
}

export function streamFailureMessage(reason?: string | null): string {
  if (reason?.startsWith('CAMERA_IN_USE_BY_OTHER_APP')) {
    return cameraInUseMessage(reason);
  }
  switch (reason) {
    case 'ACCESSIBILITY_DISABLED':
      return (
        'Screen sharing can’t start until required permissions are finished on the child phone. ' +
        'Open Clock Time on the child device, complete the permission steps, then try Start Screen again.'
      );
    case 'PROJECTION_LOST_AFTER_RESTART':
      return (
        'Screen sharing needs a fresh setup after the child phone restarted. ' +
        'Tap Start Screen again to continue.'
      );
    case 'PROJECTION_REQUIRES_RECONSENT':
      return (
        'Screen sharing needs to be set up again on the child phone. ' +
        'Tap Start Screen to continue.'
      );
    case 'PROJECTION_RECOVERY_PENDING':
      return 'Screen sharing isn’t ready yet on the child phone. Tap Start Screen again in a moment.';
    case 'PROJECTION_RECOVERY_FAILED':
      return (
        'Couldn’t start screen sharing on the child phone. ' +
        'Tap Start Screen again after permissions look complete in Clock Time.'
      );
    case 'NO_SCREEN_CAPTURE_CONSENT':
      return (
        'Screen sharing permission hasn’t been completed on the child phone yet. ' +
        'Finish permissions in Clock Time during setup, or tap Start Screen to continue.'
      );
    case 'PROJECTION_SESSION_NOT_WARM':
      return (
        'Screen sharing isn’t ready yet. Open Clock Time on the child phone briefly, wait a few seconds, then try again.'
      );
    case 'FGS_NOT_READY':
    case 'SCREEN_FGS_FAILED':
    case 'FGS_GATE_CLOSED':
      return (
        'Screen sharing couldn’t start on the child phone. Open Clock Time briefly, then try again.'
      );
    case 'AUDIO_FGS_FAILED':
      return (
        'Listening couldn’t start on the child phone. Open Clock Time briefly, then try again.'
      );
    case 'CAMERA_FGS_FAILED':
      return (
        'Camera view couldn’t start on the child phone. Open Clock Time briefly, then try again.'
      );
    case 'NO_CAPTURE_FRAMES':
      return (
        'The child phone connected, but no screen picture arrived. ' +
        'Unlock the child phone and try Start Screen again.'
      );
    case 'INVALID_STREAM_REQUEST':
      return 'Something went wrong starting live view. Tap Reconnect to try again.';
    case 'PROJECTION_RECOVERY_DEFERRED':
      return (
        'Screen sharing can’t start until required permissions are finished on the child phone. ' +
        'Open Clock Time, complete permissions, then tap Start Screen again.'
      );
    case 'AUDIO_FOCUS_DENIED':
      return (
        'Another app is using the microphone on the child phone. Close other apps and try again.'
      );
    case 'CAPTURE_BUSY':
      return 'Live view is already running on the child phone. Stop it, then try again.';
    case 'CHILD_NO_OFFER':
      return (
        'The child phone didn’t answer in time. Open Clock on the child device, keep it unlocked, ' +
        'then tap Reconnect.'
      );
    case 'SIGNALING_REJECTED':
      return (
        'Couldn’t finish connecting to the child phone. Tap Reconnect. ' +
        'If it keeps failing, open Clock on the child device and try again.'
      );
    case 'ICE_FAILED':
    case 'CONNECTION_FAILED':
      return (
        'Connection failed between the phones. Check Wi‑Fi or mobile data on both devices, then tap Reconnect.'
      );
    default:
      if (reason?.startsWith('FGS_TIMEOUT_')) {
        return (
          'Live view ended to save battery on the child phone. ' +
          'Tap Reconnect to start again.'
        );
      }
      if (reason?.startsWith('ICE_')) {
        return (
          'Connection failed between the phones. Check Wi‑Fi or mobile data on both devices, then tap Reconnect.'
        );
      }
      return 'Couldn’t start live view. Check your connection and try again.';
  }
}
