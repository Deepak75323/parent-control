import {NativeEventEmitter, NativeModules, Platform} from 'react-native';
import {streamingApi, deviceApi} from './api';
import {nativeModules} from '../nativeModules';
import {
  connectChildStream,
  disconnectStream,
  getActiveStreamRoom,
  getIceConnectionState,
  isStreamActive,
  isStreamConnecting,
  isStreamingSession,
  switchCamera,
} from './webrtcService';
import {
  ensureDeviceWebSocket,
  pauseStreamWebSocketSubscriptions,
} from './deviceWebSocket';
import {flowLog, flowNewSession, flowSetSession, flowSetTraceId, flowSetState, flowFailure, slog} from './flowLogger';
import {streamStateTransition, resetStreamState} from './streamStateMachine';
import {ensurePermissionsForStream} from './streamingPermissions';
import {
  clearPendingStreamRequest,
  type IncomingStreamRequest,
} from './streamIncomingStore';
import {
  clearCaptureDecline,
  isCaptureDeclined,
  isCapturePromptActive,
  markCaptureDeclined,
  setCapturePromptActive,
} from './streamCaptureGate';
import {
  endSession as endOwnedSession,
  getSessionSnapshot,
  syncFromNativeSnapshot,
} from './streamSessionOwner';
import {isStreamingSessionEndedError} from './signalingOfferErrors';
import {shouldSuppressBackendSessionTeardown} from './streamBootstrapGrace';

/** Silent background poll — backup if WebSocket push is missed. */
const POLL_MS = 3000;

const CAPTURE_RETRY_COOLDOWN_MS = 30_000;
/** Short debounce between retries for the same pending session (parent is waiting). */
const CAPTURE_RETRY_DEBOUNCE_MS = 3_000;
/** Camera/screen need permission dialogs + getUserMedia — 45s was aborting mid-capture. */
const NEGOTIATION_TIMEOUT_MS = 120_000;
const POST_TIMEOUT_COOLDOWN_MS = 8_000;

let timer: ReturnType<typeof setInterval> | null = null;
let activeRoom: string | null = null;
let activeSessionId: string | null = null;
let starting = false;
let lastCaptureFailedAt = 0;
let lastCaptureFailedSessionId: string | null = null;
let lastStartAttemptAt = 0;
let lastNegotiationTimeoutAt = 0;
let nativeControlSubscription: {remove: () => void} | null = null;
let stopInFlight: Promise<void> | null = null;
let pollInFlight = false;

let nativeWakeSubscription: {remove: () => void} | null = null;
let lastHandledSessionId: string | null = null;
let lastHandledAt = 0;
const DUPLICATE_REQUEST_MS = 8000;

async function reconcileNativePublisherState(): Promise<boolean> {
  if (Platform.OS !== 'android') return false;
  try {
    const running = await nativeModules.isNativeScreenStreamRunning();
    const nativeSnap = await nativeModules.getStreamSessionSnapshot();
    syncFromNativeSnapshot(
      (nativeSnap.sessionId as string | null) ?? null,
      (nativeSnap.owner as string | null) ?? null,
      running,
    );
    return running;
  } catch {
    return false;
  }
}

function hasNativePublisherOwner(): boolean {
  return getSessionSnapshot().owner === 'NATIVE_PUBLISHER';
}

function attachNativeStreamWakeListener(): void {
  if (Platform.OS !== 'android' || nativeWakeSubscription) return;
  const emitter = new NativeEventEmitter(NativeModules.ChildMonitorNative);
  nativeWakeSubscription = emitter.addListener(
    'streamRequestWake',
    (event: {roomId?: string; streamType?: string; sessionId?: string; cameraFacing?: string}) => {
      flowLog('SCREEN_SHARE_FLOW', 'native streamRequestWake event', event);
      if (!event.roomId || !event.sessionId || !event.streamType) return;
      void nativeModules.setJsStreamingListenerActive(true);
      handleIncomingStreamRequest({
        signalingRoomId: event.roomId,
        streamType: event.streamType,
        sessionId: event.sessionId,
        cameraFacing: event.cameraFacing,
      });
    },
  );
}

function detachNativeStreamWakeListener(): void {
  nativeWakeSubscription?.remove();
  nativeWakeSubscription = null;
}

async function prepareForStreamCapture(_streamType: string): Promise<void> {
  // All live streams use hidden native capture — no MainActivity wake.
}

function handleIncomingStreamRequest(pending: Omit<IncomingStreamRequest, 'receivedAt'>): void {
  const now = Date.now();
  if (pending.traceId) {
    flowSetTraceId(pending.traceId);
  }
  // Duplicate check MUST run before any state transition.  Moving streamStateTransition below this
  // guard means the active/connecting state is still visible when we check, so a second delivery of
  // the same session-id (e.g. from the native wake listener) correctly returns early instead of
  // tearing down the ongoing stream.
  if (
    pending.sessionId === lastHandledSessionId &&
    now - lastHandledAt < DUPLICATE_REQUEST_MS &&
    (starting || isStreamConnecting() || isStreamActive() || isStreamingSession(pending.signalingRoomId, pending.sessionId))
  ) {
    slog('STREAM', 'duplicate stream request ignored', {
      sessionId: pending.sessionId,
      streamType: pending.streamType,
    });
    return;
  }
  flowSetState('STREAM_REQUEST_RECEIVED');
  streamStateTransition('REQUEST_RECEIVED', 'handleIncomingStreamRequest', pending);
  flowLog('SCREEN_SHARE_FLOW', 'handleIncomingStreamRequest — entry', pending, 'handleIncomingStreamRequest');
  lastHandledSessionId = pending.sessionId;
  lastHandledAt = now;

  flowLog('SCREEN_SHARE_FLOW', 'child stream request received (JS handler)', {
    streamType: pending.streamType,
    sessionId: pending.sessionId,
    roomId: pending.signalingRoomId,
  });
  flowNewSession(pending.sessionId.slice(0, 8));
  flowSetSession(pending.sessionId);
  // App may be backgrounded (e.g. launcher icon hidden) with React still alive — restart
  // poll/stop detection without waiting for AppState foreground.
  if (!timer) {
    startStreamingListener();
    void nativeModules.setJsStreamingListenerActive(true);
  }
  const req: IncomingStreamRequest = {...pending, receivedAt: Date.now()};
  clearPendingStreamRequest();
  flowLog('STREAM', 'silent stream start — no UI', {streamType: pending.streamType});
  dumpStreamDiagnostics('handleIncomingStreamRequest').catch(() => undefined);
  startStream({
    signalingRoomId: req.signalingRoomId,
    streamType: req.streamType,
    sessionId: req.sessionId,
    cameraFacing: req.cameraFacing,
  }).catch(err => slog('STREAM', 'incoming start failed', String(err)));
}

function attachNativeStreamControlListener(): void {
  if (Platform.OS !== 'android' || nativeControlSubscription) return;
  const emitter = new NativeEventEmitter(NativeModules.ChildMonitorNative);
  nativeControlSubscription = emitter.addListener(
    'nativeStreamControl',
    (event: {command?: string}) => {
      if (event.command === 'SWITCH_CAMERA') {
        switchCamera().catch(err => slog('STREAM', 'native switch failed', String(err)));
      } else if (event.command === 'STOP') {
        stopActiveStream('native STOP event').catch(err =>
          slog('STREAM', 'native stop failed', String(err)),
        );
      }
    },
  );
}

function detachNativeStreamControlListener(): void {
  nativeControlSubscription?.remove();
  nativeControlSubscription = null;
}

type PendingSession = {
  sessionId: string;
  signalingRoomId: string;
  streamType: string;
  status?: string;
  cameraFacing?: string;
};

function isEstablishedIce(ice: string | null): boolean {
  return ice === 'connected' || ice === 'completed';
}

async function stopActiveStream(reason: string): Promise<void> {
  if (stopInFlight) {
    slog('STREAM', 'stop coalesced — already in flight', {reason});
    return stopInFlight;
  }
  stopInFlight = (async () => {
    streamStateTransition('STOPPING', 'stopActiveStream', {reason});
    slog('STREAM', 'stopping', {reason, room: activeRoom, sessionId: activeSessionId});
    void nativeModules.logStreamLifecycle('STREAM_STOP_REQUESTED', reason);
    activeRoom = null;
    activeSessionId = null;
    starting = false;
    await nativeModules.setMonitoringAudioMode(false).catch(() => undefined);
    await nativeModules.stopNativeScreenStream().catch(() => undefined);
    await disconnectStream();
    await nativeModules.notifyStreamTeardownComplete(reason).catch(() => undefined);
    endOwnedSession();
    streamStateTransition('STOPPED', 'stopActiveStream', {reason});
    resetStreamState();
    if (!isStreamActive() && !isStreamConnecting()) {
      detachNativeStreamControlListener();
    }
  })().finally(() => {
    stopInFlight = null;
  });
  return stopInFlight;
}

async function dumpStreamDiagnostics(context: string): Promise<void> {
  try {
    const [diag, caps, status] = await Promise.all([
      nativeModules.getStreamDiagnostics(),
      nativeModules.getDeviceCapabilities(),
      Promise.resolve(getStreamingStatus()),
    ]);
    flowLog('DIAG', `snapshot @ ${context}`, {
      ...diag,
      capabilities: caps,
      streaming: status,
      activeRoom,
      activeSessionId,
      sessionOwner: getSessionSnapshot(),
      starting,
      isStreamConnecting: isStreamConnecting(),
      isStreamActive: isStreamActive(),
      lastCaptureFailedAt,
    });
  } catch (err) {
    flowLog('DIAG', `snapshot failed @ ${context}`, {error: String(err)});
  }
}

function isUserDeclinedError(err: unknown): boolean {
  const msg = String(err instanceof Error ? err.message : err).toLowerCase();
  return (
    msg.includes('cancel') ||
    msg.includes('denied') ||
    msg.includes('declined') ||
    msg.includes('notallowed') ||
    msg.includes('permission') ||
    msg.includes('consent') ||
    msg.includes('not granted') ||
    msg.includes('aborted')
  );
}

async function startStream(pending: PendingSession): Promise<void> {
  flowSetSession(pending.sessionId);
  streamStateTransition('SERVICE_STARTING', 'startStream', pending);
  flowLog('SCREEN_SHARE_FLOW', 'startStream invoked', pending, 'startStream');

  if (isCaptureDeclined(pending.sessionId)) {
    slog('STREAM', 'start skipped — user declined this session', {sessionId: pending.sessionId});
    return;
  }
  if (isCapturePromptActive()) {
    slog('STREAM', 'start skipped — capture prompt already open');
    return;
  }

  if (isStreamingSession(pending.signalingRoomId, pending.sessionId)) {
    flowLog('SCREEN_SHARE_FLOW', 'already on this session — skip');
    return;
  }
  if (starting || isStreamConnecting()) {
    slog('STREAM', 'start blocked — negotiation in progress');
    return;
  }

  try {
    await prepareForStreamCapture(pending.streamType);
  } catch (err) {
    flowLog('PERMISSION_FLOW', 'foreground prep failed', {error: String(err)});
    throw err;
  }

  if (activeRoom && activeRoom !== pending.signalingRoomId) {
    if (starting || isStreamConnecting()) {
      slog('STREAM', 'new room deferred — negotiation in progress', {
        activeRoom,
        pendingRoom: pending.signalingRoomId,
      });
      return;
    }
    await stopActiveStream('new room');
  }

  if (pending.streamType === 'SCREEN') {
    await dumpStreamDiagnostics('startStream SCREEN pre-path');
    const caps = await nativeModules.getDeviceCapabilities();
    slog('STREAM', 'device capabilities', caps);
    flowLog('DIAG', 'screen path decision', {
      silentScreenCapture: caps.silentScreenCapture,
      protectionLevel: caps.protectionLevel,
      mediaProjectionGranted: caps.mediaProjectionGranted,
      deviceOwner: caps.deviceOwner,
      path: 'js-hidden-only',
    });
    // Screen capture always uses JS hidden path when the listener is active.
    // Native publisher runs only from NativeStreamingCoordinator when React is inactive.
  }

  if (pending.streamType === 'SCREEN') {
    const debounceRemaining =
      CAPTURE_RETRY_DEBOUNCE_MS - (Date.now() - lastCaptureFailedAt);
    if (
      debounceRemaining > 0 &&
      lastCaptureFailedSessionId === pending.sessionId
    ) {
      slog('STREAM', 'screen capture debounce — skipping rapid retry', {
        debounceRemainingMs: debounceRemaining,
      });
      return;
    }
  }

  starting = true;
  lastStartAttemptAt = Date.now();
  activeRoom = pending.signalingRoomId;
  activeSessionId = pending.sessionId;
  // Ack wake early so native cancels headless fallback and poll grace applies before WebRTC connects.
  void nativeModules
    .acquireJsStreamSession(pending.sessionId, pending.signalingRoomId)
    .catch(() => undefined);
  slog('STREAM', 'starting RN WebRTC', pending);

  if (pending.streamType !== 'SCREEN') {
    try {
      await ensurePermissionsForStream(pending.streamType);
      streamStateTransition('PERMISSION_VERIFIED', 'startStream', {streamType: pending.streamType});
    } catch (err) {
      starting = false;
      activeRoom = null;
      activeSessionId = null;
      slog('STREAM', 'permission check failed', String(err));
      throw err;
    }
  }

  // Do not set MODE_IN_COMMUNICATION for AUDIO — device-playback capture is designed to
  // share the child's media focus. Taking communication mode pauses YouTube / video apps.

  try {
    streamStateTransition('CAPTURE_STARTING', 'startStream', {streamType: pending.streamType});
    await connectChildStream(
      pending.signalingRoomId,
      pending.streamType,
      pending.sessionId,
      pending.cameraFacing,
    );
    clearPendingStreamRequest(pending.sessionId);
    clearCaptureDecline(pending.sessionId);
    slog('STREAM', 'WebRTC setup complete', pending.signalingRoomId);
    lastCaptureFailedAt = 0;
    lastCaptureFailedSessionId = null;
  } catch (err) {
    if (!isStreamActive()) {
      activeRoom = null;
      activeSessionId = null;
    }
    await nativeModules.setMonitoringAudioMode(false).catch(() => undefined);
    lastCaptureFailedAt = Date.now();
    lastCaptureFailedSessionId = pending.sessionId;
    if (isUserDeclinedError(err)) {
      markCaptureDeclined(pending.sessionId);
    }
    await dumpStreamDiagnostics('startStream catch');
    if (!isStreamingSessionEndedError(err)) {
      void deviceApi.reportStreamFailure(pending.sessionId, String(err)).catch(() => undefined);
    } else {
      slog('STREAM', 'start aborted — session already ended by parent', pending.sessionId);
    }
    if (!isStreamActive()) {
      await disconnectStream().catch(() => undefined);
    }
    slog('STREAM', 'start failed', String(err));
    throw err;
  } finally {
    starting = false;
  }
}

async function pollPendingStreams(): Promise<void> {
  if (pollInFlight) {
    return;
  }
  pollInFlight = true;
  try {
    const [pendingRes, activeRes] = await Promise.all([
      streamingApi.pendingSessions(),
      streamingApi.activeSession(),
    ]);
    const sessions = (pendingRes.data.data ?? []) as PendingSession[];
    const pending = sessions[0];
    const active = activeRes.data.data as PendingSession | null | undefined;
    const ice = getIceConnectionState();
    const nativeRunning = await reconcileNativePublisherState();

    slog('POLL', 'sessions', {
      pendingCount: sessions.length,
      pending: pending
        ? {sessionId: pending.sessionId, room: pending.signalingRoomId, type: pending.streamType}
        : null,
      active: active
        ? {sessionId: active.sessionId, room: active.signalingRoomId, status: active.status}
        : null,
      activeRoom,
      activeSessionId,
      starting,
      connecting: isStreamConnecting(),
      ice,
      nativeRunning,
      sessionOwner: getSessionSnapshot().owner,
    });

    if (pending) {
      const sameSession = isStreamingSession(pending.signalingRoomId, pending.sessionId);
      const staleNegotiation =
        !sameSession &&
        (activeRoom !== pending.signalingRoomId || activeSessionId !== pending.sessionId) &&
        !isEstablishedIce(ice) &&
        !nativeRunning;

      if (staleNegotiation && (starting || isStreamConnecting())) {
        slog('STREAM', 'preempt stale negotiation for newer pending session', {
          activeRoom,
          pendingRoom: pending.signalingRoomId,
        });
        await stopActiveStream('superseded by newer session');
      }

      const stuckMs = Date.now() - lastStartAttemptAt;
      if (
        (starting || isStreamConnecting()) &&
        lastStartAttemptAt > 0 &&
        stuckMs > NEGOTIATION_TIMEOUT_MS
      ) {
        slog('STREAM', 'negotiation timeout — reset and retry', {stuckMs});
        lastNegotiationTimeoutAt = Date.now();
        await stopActiveStream('negotiation timeout');
      }

      const postTimeoutCooldown =
        lastNegotiationTimeoutAt > 0
          ? POST_TIMEOUT_COOLDOWN_MS - (Date.now() - lastNegotiationTimeoutAt)
          : 0;

      if (
        !isCaptureDeclined(pending.sessionId) &&
        !isCapturePromptActive() &&
        !isStreamingSession(pending.signalingRoomId, pending.sessionId) &&
        !starting &&
        !isStreamConnecting() &&
        postTimeoutCooldown <= 0 &&
        !(hasNativePublisherOwner() && nativeRunning)
      ) {
        if (hasNativePublisherOwner() && !nativeRunning) {
          await stopActiveStream('native stream not running — retry');
        }
        await startStream(pending);
      }
      return;
    }

    const backendStillActive =
      active &&
      active.sessionId &&
      activeSessionId === active.sessionId &&
      (active.status === 'ACTIVE' || active.status === 'PENDING');

    if (backendStillActive) {
      // Do not tear down on ice=failed here — iceRecovery owns restarts for handoffs.
      // Only closed means the PeerConnection is gone and capture must be released.
      if (ice === 'closed') {
        await stopActiveStream('parent viewer gone (ICE closed)');
        return;
      }
      return;
    }

    if (
      shouldSuppressBackendSessionTeardown({
        activeSessionId,
        activeRoom,
        starting,
        connecting: isStreamConnecting(),
        streamActive: isStreamActive(),
        hasNativePublisherOwner: hasNativePublisherOwner(),
        lastStartAttemptAt,
        backendActive: active ?? null,
      })
    ) {
      slog('POLL', 'suppressing teardown — session bootstrap grace', {
        activeSessionId,
        backendActive: active?.sessionId ?? null,
        starting,
        connecting: isStreamConnecting(),
      });
      return;
    }

    if (activeRoom || activeSessionId || isStreamActive() || hasNativePublisherOwner() || starting) {
      await stopActiveStream('backend session ended or no active session');
    }

    // Backend has an ACTIVE session the child has dropped or never started (e.g. after
    // a JS restart or failed negotiation). Re-adopt it so the parent view becomes live.
    if (
      active &&
      active.sessionId &&
      active.signalingRoomId &&
      active.streamType &&
      (active.status === 'ACTIVE' || active.status === 'PENDING') &&
      !activeRoom &&
      !activeSessionId &&
      !starting &&
      !isStreamConnecting() &&
      !isCaptureDeclined(active.sessionId)
    ) {
      slog('STREAM', 'orphaned active backend session — re-adopting', {
        sessionId: active.sessionId,
        streamType: active.streamType,
      });
      await startStream({
        signalingRoomId: active.signalingRoomId,
        streamType: active.streamType,
        sessionId: active.sessionId,
      });
    }
  } catch (err) {
    slog('STREAM', 'poll error', String(err));
  } finally {
    pollInFlight = false;
  }
}

function onStreamRequest(event: {
  roomId: string;
  streamType: string;
  sessionId: string;
  cameraFacing?: string;
}): void {
  slog('STREAM', 'WS stream-request', event);
  handleIncomingStreamRequest({
    signalingRoomId: event.roomId,
    streamType: event.streamType,
    sessionId: event.sessionId,
    cameraFacing: event.cameraFacing,
  });
}

async function onStreamStop(event: {sessionId?: string; roomId?: string}): Promise<void> {
  slog('STREAM', 'WS stream-stop', event);
  const nativeRunning = await reconcileNativePublisherState();
  const hasLocalStream =
    activeRoom ||
    activeSessionId ||
    isStreamActive() ||
    nativeRunning ||
    hasNativePublisherOwner() ||
    starting ||
    isStreamConnecting();

  if (!hasLocalStream) {
    return;
  }

  const sessionMatch =
    !event.sessionId || !activeSessionId || event.sessionId === activeSessionId;
  const roomMatch = !event.roomId || !activeRoom || event.roomId === activeRoom;

  if (sessionMatch && roomMatch) {
    stopActiveStream('parent stop (WS)').catch(err =>
      slog('STREAM', 'WS stop failed', String(err)),
    );
    return;
  }

  // Device-scoped stop with mismatched ids — tear down orphan local stream.
  slog('STREAM', 'WS stream-stop forced — id mismatch', {
    event,
    activeSessionId,
    activeRoom,
  });
  stopActiveStream('parent stop (WS forced)').catch(err =>
    slog('STREAM', 'WS forced stop failed', String(err)),
  );
}

function onStreamControl(event: {sessionId?: string; command?: string}): void {
  slog('STREAM', 'WS stream-control', event);
  if (event.command === 'STOP') {
    stopActiveStream('parent stop (stream-control)').catch(err =>
      slog('STREAM', 'stream-control stop failed', String(err)),
    );
    return;
  }
  if (event.command === 'SWITCH_CAMERA') {
    if (event.sessionId && activeSessionId && event.sessionId !== activeSessionId) {
      return;
    }
    switchCamera().catch(err => slog('STREAM', 'switch camera failed', String(err)));
  }
}

export function processHeadlessStreamRequest(data: {
  roomId?: string;
  sessionId?: string;
  streamType?: string;
  cameraFacing?: string;
}): void {
  if (!data.roomId || !data.sessionId || !data.streamType) return;
  handleIncomingStreamRequest({
    signalingRoomId: data.roomId,
    streamType: data.streamType,
    sessionId: data.sessionId,
    cameraFacing: data.cameraFacing,
  });
}

export function startStreamingListener(): void {
  // Always keep the wake listener attached — background pause clears the poll timer but
  // must not drop streamRequestWake delivery while React is still alive.
  attachNativeStreamWakeListener();
  if (timer) return;
  attachNativeStreamControlListener();
  ensureDeviceWebSocket().catch(err => slog('STREAM', 'native STOMP bridge setup failed', String(err)));
  pollPendingStreams().catch(() => undefined);
  timer = setInterval(() => {
    pollPendingStreams().catch(() => undefined);
  }, POLL_MS);
  slog('STREAM', 'listener started');
}

export function pauseStreamingListener(): void {
  // Keep the native streamRequestWake listener attached while backgrounded so a parent
  // camera request can still reach JS when the RN runtime is alive (icon hidden / lock screen).
  attachNativeStreamWakeListener();
  pauseStreamWebSocketSubscriptions();
  const streamLive =
    isStreamActive() ||
    isStreamConnecting() ||
    starting ||
    hasNativePublisherOwner();
  if (!streamLive) {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
    detachNativeStreamControlListener();
  }
  void nativeModules.logStreamLifecycle(
    'STREAM_LISTENER_PAUSED',
    streamLive
      ? 'poll timer kept for parent-stop detection — wake listener stays attached'
      : 'poll timer stopped — wake listener stays attached for hidden-app camera',
  );
  slog('STREAM', 'listener paused', {
    streamLive,
    pollKept: streamLive,
  });
}

export function stopStreamingListener(): void {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
  detachNativeStreamControlListener();
  detachNativeStreamWakeListener();
  pauseStreamWebSocketSubscriptions();
  lastCaptureFailedAt = 0;
  stopActiveStream('listener stopped').catch(() => undefined);
}

export function getStreamingStatus(): {
  activeRoom: string | null;
  iceState: string | null;
  sessionOwner: ReturnType<typeof getSessionSnapshot>;
} {
  return {
    activeRoom: getActiveStreamRoom() ?? activeRoom,
    iceState: getIceConnectionState(),
    sessionOwner: getSessionSnapshot(),
  };
}
