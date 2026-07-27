import {monitoringApi} from './api';
import {disconnectViewer} from './webrtcService';
import {
  getSessionSnapshot,
  clearAllStreamListeners,
  getSessionIdForBackendStop,
  takeSessionIdForBackendStop,
} from './streamSessionOwner';
import {track} from './telemetry';
import {TelemetryEvents} from './telemetryEvents';

/**
 * Best-effort: end the backend ScreenSession so the child releases camera/mic.
 * Safe to call after local disconnect cleared in-memory session state (uses sticky id).
 */
export async function stopBackendStreamSession(
  sessionId?: string | null,
): Promise<boolean> {
  const id = sessionId?.trim() || getSessionIdForBackendStop();
  if (!id) {
    return false;
  }
  try {
    await monitoringApi.stopScreenShare(id);
    takeSessionIdForBackendStop();
    return true;
  } catch {
    // Token may already be invalid; sticky id kept so a later attempt can retry.
    return false;
  }
}

export async function teardownActiveStreamOnAuthEnd(): Promise<void> {
  const {sessionId, roomId, phase} = getSessionSnapshot();
  const backendSessionId = getSessionIdForBackendStop();
  const hasLiveSession =
    !!backendSessionId ||
    !!sessionId ||
    (!!roomId && phase !== 'IDLE' && phase !== 'TEARDOWN');

  if (hasLiveSession || backendSessionId) {
    track(TelemetryEvents.STREAM_AUTH_TEARDOWN, {
      has_session_id: !!backendSessionId,
      phase: phase ?? 'unknown',
    });
  }

  // Stop backend BEFORE clearing local WebRTC — child green privacy indicators depend on this.
  if (backendSessionId) {
    await stopBackendStreamSession(backendSessionId);
  }

  if (hasLiveSession || backendSessionId || sessionId) {
    try {
      await disconnectViewer();
    } catch {
      // ignore
    }
  }

  // If disconnectViewer ran first elsewhere, still consume sticky after a final stop attempt.
  const leftover = getSessionIdForBackendStop();
  if (leftover) {
    await stopBackendStreamSession(leftover);
  }

  clearAllStreamListeners();
}
