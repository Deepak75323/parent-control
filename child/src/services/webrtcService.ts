import {Platform} from 'react-native';
import {
  mediaDevices,
  RTCPeerConnection,
  RTCSessionDescription,
  RTCIceCandidate,
  MediaStream,
  MediaStreamTrack,
} from 'react-native-webrtc';
import {deviceApi, signalingApi, streamingApi} from './api';
import {nativeModules} from '../nativeModules';
import {subscribeSignalingRoom, unsubscribeSignalingRoom} from './deviceWebSocket';
import {slog, flowLog} from './flowLogger';
import {streamStateTransition} from './streamStateMachine';
import {ensurePermissionsForStream} from './streamingPermissions';
import {defaultIceConfig, isIceConfigStale, logIce, orderedSignalingMessages, adaptIceServersForDev} from './webrtcIce';
import {getApiBaseUrl} from '../utils/config';
import {markCaptureDeclined, setCapturePromptActive} from './streamCaptureGate';
import {
  beginJsSession,
  endSession,
  markJsActive,
  setSessionPhase,
} from './streamSessionOwner';
import {attachIceRecovery} from './iceRecovery';
import {attemptsAtConnectEntry} from './connectionAttemptBudget';
import {
  StreamingSessionEndedError,
  classifyOfferPostFailure,
  isStreamingSessionEndedError,
} from './signalingOfferErrors';

export type SignalingPayload = {
  id?: string;
  messageType: string;
  payload: string;
  senderRole: string;
};

async function loadIceConfig(
  sessionId?: string,
): Promise<ReturnType<typeof defaultIceConfig>> {
  const apiServers: RTCIceServer[] = [];
  let credentialExpiresAt: number | undefined;
  try {
    // P2-4: bind TURN credentials to the streaming session so a leak only relays this
    // session, not arbitrary future streams, and only for ≤15 min.
    const {data} = await streamingApi.webrtcConfig(sessionId);
    credentialExpiresAt = data.data?.credentialExpiresAt;
    for (const s of data.data?.iceServers ?? []) {
      if (s.username && s.credential) {
        apiServers.push({
          urls: s.urls,
          username: s.username,
          credential: s.credential,
        });
      } else if (s.urls?.length) {
        apiServers.push({urls: s.urls});
      }
    }
  } catch (err) {
    slog('ICE', 'webrtc-config fetch failed', String(err));
  }
  const ice = defaultIceConfig(
    adaptIceServersForDev(apiServers, getApiBaseUrl()),
    credentialExpiresAt,
  );
  slog('ICE', 'config loaded', {
    servers: ice.iceServers.length,
    policy: ice.iceTransportPolicy,
    expiresAt: ice.credentialExpiresAt,
  });
  return ice;
}

let pc: RTCPeerConnection | null = null;
let connectionAttempts = 0;
const MAX_CONNECTION_ATTEMPTS = 3;
let activeMediaStream: MediaStream | null = null;
let pollTimer: ReturnType<typeof setInterval> | null = null;
let activeRoomId: string | null = null;
let activeSessionId: string | null = null;
let activeStreamType: 'SCREEN' | 'CAMERA' | 'AUDIO' = 'SCREEN';
let connecting = false;
let signalingBusy = false;
let iceMonitorTimer: ReturnType<typeof setInterval> | null = null;
/**
 * True while the Android getDisplayMedia() system dialog is on screen.
 * Guards against a second concurrent attempt being triggered by the 3-second
 * background poll while the user is still interacting with the dialog.
 */
let displayMediaPending = false;
let cameraSwitchInFlight = false;
/** Audio stream captured alongside the camera; stopped in disconnectStream CAMERA cleanup. */
let cameraAudioStream: MediaStream | null = null;
/** Audio track captured alongside camera; added to PC directly (NOT via stream.addTrack which crashes with cross-stream tracks). */
let cameraAudioTrack: MediaStreamTrack | null = null;
/** System audio from AudioPlaybackCapture during screen share. */
let screenDeviceAudioTrack: MediaStreamTrack | null = null;
/** Tracks the current camera facing so switchCamera toggles correctly (CameraX does not expose facingMode via getSettings). */
let currentCameraFacing: 'user' | 'environment' = 'user';
let detachIceRecovery: (() => void) | null = null;
let detachNetworkQualityMonitor: (() => void) | null = null;
const seenIds = new Set<string>();
const pendingIceCandidates: RTCIceCandidateInit[] = [];

function startIceMonitor(label: string): void {
  if (iceMonitorTimer) clearInterval(iceMonitorTimer);
  iceMonitorTimer = setInterval(() => {
    if (!pc) return;
    slog('MONITOR', label, {
      ice: pc.iceConnectionState,
      connection: pc.connectionState,
      gathering: pc.iceGatheringState,
      signaling: pc.signalingState,
      hasRemote: !!pc.remoteDescription,
      hasLocal: !!pc.localDescription,
      pendingIce: pendingIceCandidates.length,
    });
  }, 2000);
}

function stopIceMonitor(): void {
  if (iceMonitorTimer) {
    clearInterval(iceMonitorTimer);
    iceMonitorTimer = null;
  }
}

function parseSessionDescription(payload: string): RTCSessionDescriptionInit {
  try {
    return JSON.parse(payload) as RTCSessionDescriptionInit;
  } catch {
    return {type: 'answer', sdp: payload};
  }
}

function sdpHasVideoMline(sdp: string | undefined): boolean {
  return !!sdp && /^m=video /m.test(sdp);
}

const TRACK_LIVE_WAIT_MS = 200;
const TRACK_LIVE_ATTEMPTS = 25;

async function waitForCaptureTrackLive(track: {readyState?: string} | undefined): Promise<void> {
  if (!track) {
    throw new Error('Screen or camera didn’t start. Please try again.');
  }
  if (track.readyState === 'live') {
    return;
  }
  for (let i = 0; i < TRACK_LIVE_ATTEMPTS; i++) {
    if (track.readyState === 'ended') {
      throw new Error(
        'Live view stopped before the first picture. Wait a few seconds and try again.',
      );
    }
    if (track.readyState === 'live') {
      return;
    }
    await new Promise<void>(resolve => setTimeout(resolve, TRACK_LIVE_WAIT_MS));
  }
  flowLog('CAPTURE', 'track not live after wait — proceeding with offer', {
    readyState: track.readyState,
  });
}

export function isStreamActive(): boolean {
  return pc != null && activeRoomId != null;
}

export function isStreamConnecting(): boolean {
  return connecting || signalingBusy || displayMediaPending;
}

export function isStreamingSession(roomId: string, sessionId: string): boolean {
  return activeRoomId === roomId && activeSessionId === sessionId && pc != null;
}

export function getIceConnectionState(): string | null {
  return pc?.iceConnectionState ?? null;
}

export function getActiveStreamRoom(): string | null {
  return activeRoomId;
}

async function flushPendingIceCandidates(): Promise<void> {
  if (!pc?.remoteDescription) return;
  slog('ICE', `flushing ${pendingIceCandidates.length} queued candidates`);
  while (pendingIceCandidates.length > 0) {
    const candidate = pendingIceCandidates.shift();
    if (!candidate || !pc) continue;
    try {
      await pc.addIceCandidate(new RTCIceCandidate(candidate));
      slog('ICE', 'queued candidate applied', candidate.candidate?.slice(0, 50));
    } catch (err) {
      slog('ICE', 'queued candidate failed', String(err));
      pendingIceCandidates.unshift(candidate);
      break;
    }
  }
}

async function applyRemoteAnswer(payload: string, messageId: string): Promise<boolean> {
  if (!pc || seenIds.has(messageId)) return false;

  const signaling = pc.signalingState ?? 'unknown';
  // After iceRestart createOffer we are have-local-offer, but remoteDescription may
  // still be the *previous* answer. The old early-return blocked every new answer and
  // left the child stuck in have-local-offer across wifi↔cellular handoffs.
  if (signaling === 'stable' && pc.remoteDescription?.type === 'answer') {
    slog('SDP', 'answer skipped — already stable', messageId);
    return true;
  }
  if (signaling !== 'have-local-offer' && pc.localDescription?.type !== 'offer') {
    slog('SDP', 'answer skipped — no local offer yet', {messageId, signaling});
    return false;
  }
  signalingBusy = true;
  try {
    await pc.setRemoteDescription(new RTCSessionDescription(parseSessionDescription(payload)));
    streamStateTransition('REMOTE_DESCRIPTION_SET', 'applyRemoteAnswer', {messageId});
    seenIds.add(messageId);
    slog('SDP', 'remote ANSWER applied', messageId);
    flowLog('WEBRTC_FLOW', sdpHasVideoMline(pc.remoteDescription?.sdp) ? 'ANSWER_CONTAINS_VIDEO' : 'ANSWER_MISSING_VIDEO', {
      roomId: activeRoomId,
    });
    logIce('child', 'remote answer applied');
    await flushPendingIceCandidates();
    return true;
  } catch (err) {
    slog('SDP', 'remote ANSWER rejected (stale/mismatch)', String(err));
    logIce('child', 'remote answer rejected', String(err));
    return false;
  } finally {
    signalingBusy = false;
  }
}

async function applyRemoteIce(payload: string, messageId: string): Promise<void> {
  if (!pc || seenIds.has(messageId)) return;
  const candidate = JSON.parse(payload) as RTCIceCandidateInit;
  if (!pc.remoteDescription) {
    pendingIceCandidates.push(candidate);
    seenIds.add(messageId);
    slog('ICE', 'candidate queued (no remote description yet)', candidate.candidate?.slice(0, 50));
    return;
  }
  try {
    await pc.addIceCandidate(new RTCIceCandidate(candidate));
    seenIds.add(messageId);
    slog('ICE', 'candidate applied', candidate.candidate?.slice(0, 50));
    logIce('child', 'ICE applied', candidate.candidate?.slice(0, 40));
  } catch (err) {
    slog('ICE', 'candidate failed', String(err));
    logIce('child', 'ICE failed', String(err));
  }
}

async function handleSignalingMessage(msg: SignalingPayload): Promise<void> {
  if (!pc || !activeRoomId) {
    slog('SIGNAL', 'skip — no pc or room', {hasPc: !!pc, room: activeRoomId});
    return;
  }
  if (msg.senderRole === 'CHILD') return;
  const messageId = msg.id ?? `${msg.messageType}-${msg.payload.slice(0, 32)}`;
  slog('SIGNAL', 'incoming message', {
    type: msg.messageType,
    from: msg.senderRole,
    id: messageId,
  });

  if (msg.messageType === 'answer') {
    await applyRemoteAnswer(msg.payload, messageId);
    return;
  }
  if (msg.messageType === 'ice') {
    await applyRemoteIce(msg.payload, messageId);
  }
}

async function pollSignaling(roomId: string): Promise<boolean> {
  try {
    const {data} = await signalingApi.recent(roomId);
    const messages = orderedSignalingMessages(data.data ?? []);
    slog('POLL', `got ${messages.length} messages`, roomId);

    let latestAnswer: SignalingPayload | null = null;
    for (const msg of messages) {
      const payload: SignalingPayload = {
        id: msg.id,
        messageType: msg.messageType,
        payload: msg.payload,
        senderRole: msg.senderRole,
      };
      if (payload.messageType === 'answer' && payload.senderRole !== 'CHILD') {
        latestAnswer = payload;
        continue;
      }
      await handleSignalingMessage(payload);
    }

    if (latestAnswer) {
      const messageId =
        latestAnswer.id ?? `${latestAnswer.messageType}-${latestAnswer.payload.slice(0, 32)}`;
      if (await applyRemoteAnswer(latestAnswer.payload, messageId)) {
        return true;
      }
    }

    return pc?.remoteDescription?.type === 'answer';
  } catch (err) {
    slog('POLL', 'signaling fetch failed', String(err));
    logIce('child', 'signaling poll error', String(err));
    return false;
  }
}

async function waitForRemoteAnswer(roomId: string, timeoutMs = 90000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  const startTime = Date.now();
  slog('SDP', 'Waiting for remote answer', {roomId, timeoutMs, deadline});
  
  let pollAttempts = 0;
  const maxPollAttempts = Math.floor(timeoutMs / 250);
  
  while (Date.now() < deadline && pc && !pc.remoteDescription) {
    pollAttempts++;
    
    // Check if peer connection is still viable
    if (!pc || pc.signalingState === 'closed') {
      slog('SDP', 'PeerConnection lost during answer wait', {roomId, pollAttempts});
      return false;
    }
    
    try {
      const applied = await Promise.race([
        pollSignaling(roomId),
        new Promise<boolean>((_, reject) => 
          setTimeout(() => reject(new Error('Polling timeout')), 5000)
        )
      ]);
      
      if (applied || pc?.remoteDescription) {
        const elapsedMs = Date.now() - startTime;
        slog('SDP', 'answer received during wait', {roomId, elapsedMs, pollAttempts});
        flowLog('SCREEN_SHARE_FLOW', 'answer received', {roomId, elapsedMs});
        return true;
      }
    } catch (pollError) {
      slog('SDP', 'Polling error during answer wait', {
        error: String(pollError), 
        roomId, 
        pollAttempts,
        remaining: Math.max(0, deadline - Date.now())
      });
      
      // If we're getting consistent polling errors, bail out early
      if (pollAttempts > 10 && String(pollError).includes('timeout')) {
        slog('SDP', 'Too many polling timeouts, aborting answer wait', {roomId, pollAttempts});
        return false;
      }
    }
    
    await new Promise<void>(resolve => setTimeout(resolve, 250));
  }
  
  const ok = pc?.remoteDescription?.type === 'answer';
  const elapsedMs = Date.now() - startTime;
  
  if (!ok) {
    slog('SDP', 'answer wait timed out', {
      roomId, 
      ice: pc?.iceConnectionState,
      signalingState: pc?.signalingState,
      elapsedMs,
      pollAttempts,
      maxAttempts: maxPollAttempts
    });
  }
  
  return ok;
}

async function postChildOffer(
  roomId: string,
  sessionId: string,
  offer: RTCSessionDescriptionInit,
  label: string,
): Promise<void> {
  try {
    await signalingApi.post(roomId, 'offer', JSON.stringify(offer), sessionId);
  } catch (signalingError) {
    const kind = classifyOfferPostFailure(signalingError);
    if (kind === 'duplicate_ok') {
      slog('SDP', `${label}: duplicate offer treated as success`, {roomId, sessionId});
      return;
    }
    if (kind === 'session_ended') {
      slog('SDP', `${label}: session already ended — abort quietly`, {roomId, sessionId});
      throw new StreamingSessionEndedError();
    }
    slog('SDP', `${label}: Failed to send offer via signaling`, {
      error: String(signalingError),
      roomId,
      sessionId,
    });
    throw new Error(`Failed to send offer: ${String(signalingError)}`);
  }
}

async function renegotiateChildOffer(): Promise<void> {
  if (!pc || !activeRoomId || !activeSessionId) return;
  setSessionPhase('RECONNECTING');
  void nativeModules.logStreamLifecycle('ICE_RENEGOTIATE', activeSessionId);
  const offer = await pc.createOffer({iceRestart: true});
  await pc.setLocalDescription(offer);
  await postChildOffer(activeRoomId, activeSessionId, offer, 'ice-restart');
  slog('ICE', 'ice-restart offer sent', {roomId: activeRoomId});
}

function attachConnectionHandlers(peer: RTCPeerConnection): void {
  detachIceRecovery?.();
  detachNetworkQualityMonitor?.();
  detachIceRecovery = attachIceRecovery(peer, {
    // Network handoffs need an SDP iceRestart offer — restartIce() alone keeps
    // stale host candidates on the old Wi‑Fi/cellular interface.
    onRestartIce: async () => {
      void nativeModules.logStreamLifecycle('ICE_RESTART', activeSessionId ?? 'no-session');
      StreamSessionRegistryMarkReconnecting();
      slog('ICE', 'ICE restart initiated', {
        roomId: activeRoomId,
        sessionId: activeSessionId,
        currentState: peer.iceConnectionState,
        connectionState: peer.connectionState,
        signalingState: peer.signalingState,
      });
      await renegotiateChildOffer();
    },
    onRenegotiate: renegotiateChildOffer,
    onFatal: reason => {
      slog('PC', 'ICE recovery exhausted - connection failure', {
        reason,
        roomId: activeRoomId,
        sessionId: activeSessionId,
        finalIceState: peer.iceConnectionState,
        finalConnectionState: peer.connectionState,
      });

      // Parent may already have stopped — don't report failure for a dead session.
      const sessionAlreadyEnded =
        reason === 'SESSION_ALREADY_ENDED' || reason.includes('SESSION_ALREADY_ENDED');
      if (activeSessionId && !sessionAlreadyEnded) {
        deviceApi.reportStreamFailure(activeSessionId, reason).catch(() => undefined);
      }

      try {
        streamStateTransition(
          sessionAlreadyEnded ? 'STOPPED' : 'FAILED',
          sessionAlreadyEnded ? 'SESSION_ALREADY_ENDED' : 'ICE_RECOVERY_EXHAUSTED',
          {
            reason,
            iceState: peer.iceConnectionState,
            connectionState: peer.connectionState,
          },
        );
      } catch (cleanupError) {
        slog('PC', 'Cleanup after ICE failure error', String(cleanupError));
      }

      // Must release camera/mic — otherwise Android green privacy indicators stick
      // after the parent already shows Connection failed / child network loss.
      disconnectStream().catch(err =>
        slog('PC', 'disconnectStream after ICE fatal failed', String(err)),
      );
    },
    reportEvent: (event, attrs) => {
      const enhancedAttrs = {
        ...attrs,
        roomId: activeRoomId,
        sessionId: activeSessionId,
        streamType: activeStreamType,
        timestamp: Date.now()
      };
      
      slog('ICE', event, enhancedAttrs);
      void nativeModules.logStreamLifecycle(`ICE_${event.toUpperCase()}`, JSON.stringify(enhancedAttrs));
      
      streamingApi
        .reportIceConnectivity({
          streamType: activeStreamType,
          sessionId: activeSessionId ?? undefined,
          iceConnectionState: peer.iceConnectionState ?? 'unknown',
          failureReason: event.includes('failed') ? event : undefined,
        })
        .catch(() => undefined);
    },
  });

    peer.oniceconnectionstatechange = () => {
      const state = peer.iceConnectionState ?? 'unknown';
      const connectionState = peer.connectionState ?? 'unknown';
      const timestamp = Date.now();
      
      slog('PC', 'iceConnectionState changed', {
        state,
        connectionState,
        roomId: activeRoomId,
        sessionId: activeSessionId,
        streamType: activeStreamType
      });
      logIce('child', 'iceConnectionState', state);
      
      if (state === 'connected' || state === 'completed') {
        streamStateTransition('CONNECTED', 'oniceconnectionstatechange', {state, roomId: activeRoomId});
        markJsActive();
        flowLog('WEBRTC_FLOW', 'ICE_CONNECTED', {state, roomId: activeRoomId, connectionState});
        
        if (activeStreamType === 'CAMERA') {
          flowLog('CAMERA_FLOW', 'ICE_CONNECTED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        } else if (activeStreamType === 'AUDIO') {
          flowLog('AUDIO_FLOW', 'ICE_CONNECTED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        } else if (activeStreamType === 'SCREEN') {
          flowLog('SCREEN_SHARE_FLOW', 'ICE_CONNECTED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        }
        
        streamingApi
          .reportIceConnectivity({
            streamType: activeStreamType,
            sessionId: activeSessionId ?? undefined,
            iceConnectionState: state,
          })
          .catch(() => undefined);
          
      } else if (state === 'failed') {
        slog('PC', 'ICE connection FAILED - triggering recovery', {
          state,
          connectionState,
          roomId: activeRoomId,
          sessionId: activeSessionId,
          streamType: activeStreamType
        });
        
        streamStateTransition('ICE_FAILED', 'oniceconnectionstatechange', {state, roomId: activeRoomId});
        
        if (activeStreamType === 'CAMERA') {
          flowLog('CAMERA_FLOW', 'ICE_FAILED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        } else if (activeStreamType === 'AUDIO') {
          flowLog('AUDIO_FLOW', 'ICE_FAILED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        } else if (activeStreamType === 'SCREEN') {
          flowLog('SCREEN_SHARE_FLOW', 'ICE_FAILED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        }
        
      } else if (state === 'disconnected') {
        slog('PC', 'ICE connection DISCONNECTED - will attempt recovery if not resolved quickly', {
          state,
          connectionState,
          roomId: activeRoomId,
          sessionId: activeSessionId,
          streamType: activeStreamType
        });
        
        streamStateTransition('ICE_DISCONNECTED', 'oniceconnectionstatechange', {state, roomId: activeRoomId});
        
        if (activeStreamType === 'CAMERA') {
          flowLog('CAMERA_FLOW', 'ICE_DISCONNECTED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        } else if (activeStreamType === 'AUDIO') {
          flowLog('AUDIO_FLOW', 'ICE_DISCONNECTED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        } else if (activeStreamType === 'SCREEN') {
          flowLog('SCREEN_SHARE_FLOW', 'ICE_DISCONNECTED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        }
        
      } else if (state === 'checking') {
        slog('PC', 'ICE checking - attempting connection', {
          state,
          connectionState,
          roomId: activeRoomId,
          sessionId: activeSessionId
        });
        
      } else if (state === 'closed') {
        slog('PC', 'ICE connection CLOSED', {
          state,
          connectionState,
          roomId: activeRoomId,
          sessionId: activeSessionId
        });
        
        streamStateTransition('ICE_CLOSED', 'oniceconnectionstatechange', {state, roomId: activeRoomId});
      }
    };

    // Also monitor the general connection state for additional diagnostics
    peer.onconnectionstatechange = () => {
      const connectionState = peer.connectionState ?? 'unknown';
      const iceState = peer.iceConnectionState ?? 'unknown';

      slog('PC', 'connectionState changed', {
        connectionState,
        iceState,
        roomId: activeRoomId,
        sessionId: activeSessionId,
        streamType: activeStreamType,
      });
      logIce('child', 'connectionState', connectionState);

      if (connectionState === 'failed') {
        slog('PC', 'PeerConnection FAILED - connection state failure', {
          connectionState,
          iceState,
          roomId: activeRoomId,
          sessionId: activeSessionId,
        });
      } else if (connectionState === 'disconnected') {
        slog('PC', 'PeerConnection DISCONNECTED', {
          connectionState,
          iceState,
          roomId: activeRoomId,
          sessionId: activeSessionId,
        });
      } else if (connectionState === 'connected') {
        slog('PC', 'PeerConnection CONNECTED', {
          connectionState,
          iceState,
          roomId: activeRoomId,
          sessionId: activeSessionId,
        });
      }
    };
  peer.onicegatheringstatechange = () => {
    const state = peer.iceGatheringState ?? 'unknown';
    if (state === 'gathering') {
      streamStateTransition('ICE_CONNECTING', 'onicegatheringstatechange', {state});
    }
    slog('PC', 'iceGatheringState', state);
  };

  detachNetworkQualityMonitor = startNetworkQualityMonitoring(peer);
  slog('PC', 'Network quality monitoring started', {roomId: activeRoomId, sessionId: activeSessionId});
}

/** Bridge to native registry without importing Kotlin from TS. */
function StreamSessionRegistryMarkReconnecting(): void {
  void nativeModules.logStreamLifecycle('SESSION_RECONNECTING', activeSessionId ?? 'no-session');
}

export async function connectChildStream(
  roomId: string,
  streamType: string,
  sessionId: string,
  cameraFacing?: string,
): Promise<void> {
  const startTime = Date.now();
  let lastError: Error | null = null;
  // Fresh budget per connect call — never inherit exhaustion from a prior session.
  connectionAttempts = attemptsAtConnectEntry(connectionAttempts);

  while (connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
    try {
      await connectChildStreamImpl(roomId, streamType, sessionId, cameraFacing);
      // Success - exit retry loop
      const elapsedMs = Date.now() - startTime;
      slog('FLOW', 'connectChildStream SUCCESS', {
        roomId, 
        streamType, 
        sessionId, 
        totalAttempts: connectionAttempts + 1,
        elapsedMs
      });
      return;
    } catch (error) {
      lastError = error as Error;
      const currentAttempt = connectionAttempts + 1;
      slog('FLOW', 'connectChildStream attempt failed', {
        error: String(error),
        attempt: currentAttempt,
        maxAttempts: MAX_CONNECTION_ATTEMPTS,
        roomId,
        streamType,
        sessionId
      });
      
      // If this was the last attempt, don't wait - just throw
      if (currentAttempt >= MAX_CONNECTION_ATTEMPTS) {
        slog('FLOW', 'connectChildStream exhausted all attempts', {
          totalAttempts: currentAttempt,
          finalError: String(error)
        });
        break;
      }
      
      // Exponential backoff: 1s, 2s, 4s
      const delayMs = Math.min(1000 * Math.pow(2, currentAttempt - 1), 4000);
      slog('FLOW', 'connectChildStream retrying after delay', {
        delayMs,
        nextAttempt: currentAttempt + 1,
        roomId
      });
      
      await new Promise(resolve => setTimeout(resolve, delayMs));
    }
  }
  
  // If we get here, all attempts have failed
  const totalElapsedMs = Date.now() - startTime;
  slog('FLOW', 'connectChildStream FINAL_FAILURE', {
    totalAttempts: connectionAttempts,
    totalElapsedMs,
    lastError: String(lastError)
  });
  
  throw lastError || new Error('Connection failed after maximum attempts');
}

async function connectChildStreamImpl(
  roomId: string,
  streamType: string,
  sessionId: string,
  cameraFacing?: string,
): Promise<void> {
  if (isStreamingSession(roomId, sessionId)) {
    slog('PC', 'already streaming session — skip', {roomId, sessionId});
    return;
  }
  if (connecting) {
    slog('PC', 'connect already in progress — skip duplicate', {roomId, sessionId});
    return;
  }

  connecting = true;
    connectionAttempts++;
    streamStateTransition('WEBRTC_CONNECTING', 'connectChildStreamImpl', {roomId, streamType, sessionId});
    slog('FLOW', 'connectChildStreamImpl START', {roomId, streamType, sessionId, attempt: connectionAttempts});
    
    try {
    await disconnectStream(false);
    seenIds.clear();
    pendingIceCandidates.length = 0;
    activeRoomId = roomId;
    activeSessionId = sessionId;
    activeStreamType = streamType;
    const correlationId = beginJsSession(roomId, sessionId);
    setSessionPhase('NEGOTIATING');
    slog('SESSION', 'correlation', {correlationId});
    void nativeModules.acquireJsStreamSession(sessionId, roomId).catch(() => undefined);

    const ice = await loadIceConfig(sessionId);
    slog('PC', 'creating peer', {roomId, streamType, sessionId});
    logIce('child', 'creating peer', `${ice.iceServers.length} ice servers, type=${streamType}`);

    subscribeSignalingRoom(roomId, payload => {
      handleSignalingMessage({
        id: payload.id as string | undefined,
        messageType: String(payload.messageType ?? ''),
        payload: String(payload.payload ?? ''),
        senderRole: String(payload.senderRole ?? ''),
      }).catch(err => slog('WS', 'handler error', String(err)));
    });

    await ensurePermissionsForStream(streamType);
    streamStateTransition('PERMISSION_VERIFIED', 'connectChildStreamImpl', {streamType});

    let stream: MediaStream;
    if (streamType === 'AUDIO') {
      // One-way listening = ambient room mic (surrounding sound), NOT system playback.
      // System/YouTube audio belongs on SCREEN share only.
      flowLog('PERMISSION_FLOW', 'ambient mic capture for one-way listening', {sessionId});
      await nativeModules.setMonitoringAudioMode(false).catch(() => undefined);
      const data = await nativeModules.getHiddenAmbientAudioMediaStream();
      stream = new MediaStream({
        streamId: data.streamId,
        streamReactTag: data.streamId,
        tracks: [data.track],
      } as {streamId: string; streamReactTag: string; tracks: object[]});
      const audioTrack = stream.getAudioTracks()[0];
      if (!audioTrack) {
        throw new Error(
          'Listening couldn’t start. Grant microphone permission on the child phone, then try again.',
        );
      }
      audioTrack.enabled = true;
      slog('AUDIO', 'Ambient mic track ready for one-way listening', {
        sessionId,
        trackId: audioTrack.id,
        enabled: audioTrack.enabled,
        readyState: audioTrack.readyState,
      });
      flowLog('AUDIO_FLOW', 'AMBIENT_MIC_STARTED', {
        sessionId,
        streamId: data.streamId,
        trackId: audioTrack.id,
      });
    } else if (streamType === 'CAMERA') {
      flowLog('CAMERA_FLOW', 'CAMERA_COMMAND_RECEIVED', {sessionId, facing: cameraFacing});
      const facing = cameraFacing === 'environment' ? 'environment' : 'user';
      currentCameraFacing = facing;
      
      // Force camera access from other apps before starting stream
      const cameraId = facing === 'user' ? '1' : '0'; // Standard camera IDs: front=1, back=0
      flowLog('CAMERA_FLOW', 'FORCING_CAMERA_ACCESS', {sessionId, facing, cameraId});
      try {
        const forceSuccess = await nativeModules.forceCameraAccess(cameraId);
        flowLog('CAMERA_FLOW', 'CAMERA_FORCE_RESULT', {sessionId, success: forceSuccess, cameraId});
        if (!forceSuccess) {
          slog('CAMERA', `Warning: Failed to force camera access for ${facing} camera (ID: ${cameraId}), proceeding anyway`);
        } else {
          slog('CAMERA', `Successfully forced camera access for ${facing} camera (ID: ${cameraId})`);
        }
      } catch (error) {
        slog('CAMERA', `Camera force access error: ${error}, proceeding with normal camera access`);
      }
      
      flowLog('CAMERA_FLOW', 'FOREGROUND_SERVICE_STARTING', {sessionId, facing});
      const data = await nativeModules.getHiddenCameraMediaStream(facing);
      flowLog('CAMERA_FLOW', 'CAMERA_OPENED', {sessionId, streamId: data.streamId, facing});
      stream = new MediaStream({
        streamId: data.streamId,
        streamReactTag: data.streamId,
        tracks: [data.track],
      } as {streamId: string; streamReactTag: string; tracks: object[]});
      const videoTrack = stream.getVideoTracks()[0];
      flowLog('CAMERA_FLOW', 'VIDEO_TRACK_CREATED', {
        sessionId,
        trackId: videoTrack?.id,
        enabled: videoTrack?.enabled,
        readyState: videoTrack?.readyState,
      });
      await waitForCaptureTrackLive(videoTrack);
      // Camera is video-only — do not capture ambient mic.
      cameraAudioTrack = null;
      cameraAudioStream = null;
    } else {
      flowLog('MEDIA_PROJECTION', 'hidden screen capture — no UI', {sessionId});
      const data = await nativeModules.getHiddenScreenMediaStream();
      stream = new MediaStream({
        streamId: data.streamId,
        streamReactTag: data.streamId,
        tracks: [data.track],
      } as {streamId: string; streamReactTag: string; tracks: object[]});

      const videoTrack = stream.getVideoTracks()[0];
      slog('CAPTURE', 'display media granted', {
        videoTracks: stream.getVideoTracks().length,
        trackId: videoTrack?.id,
        enabled: videoTrack?.enabled,
        readyState: videoTrack?.readyState,
      });
      flowLog('MEDIA_PROJECTION', 'VIDEO_TRACK_CREATED', {
        trackId: videoTrack?.id,
        enabled: videoTrack?.enabled,
        readyState: videoTrack?.readyState,
      });

      // On Android 14, if MediaProjectionService did not start in time the
      // projection token is immediately revoked, which makes the video track
      // end right away.  Detect this early so the caller can show a clear
      // error instead of sending a useless offer with a dead track.
      if (!videoTrack || videoTrack.readyState === 'ended') {
        stream.getTracks().forEach(t => t.stop());
        throw new Error(
          'Screen sharing stopped almost immediately. Wait a few seconds, then try again.',
        );
      }

      videoTrack.addEventListener('ended', () => {
        slog('CAPTURE', 'video track ended — capture stopped');
        disconnectStream().catch(err => slog('CAPTURE', 'cleanup after track end failed', String(err)));
      });
      videoTrack.addEventListener('mute', () => {
        slog('CAPTURE', 'video track muted');
      });
      await waitForCaptureTrackLive(videoTrack);

      // Screen-share system audio via AudioPlaybackCapture — never steal audio focus or
      // switch to MODE_IN_COMMUNICATION (that freezes YouTube/Spotify on the child).
      try {
        const playbackOk = await nativeModules.startDevicePlaybackCapture();
        if (playbackOk) {
          const audioData = await nativeModules.getHiddenPlaybackAudioMediaStream();
          const audioTrack = new MediaStream({
            streamId: audioData.streamId,
            streamReactTag: audioData.streamId,
            tracks: [audioData.track],
          } as {streamId: string; streamReactTag: string; tracks: object[]}).getAudioTracks()[0];
          if (audioTrack) {
            audioTrack.enabled = true;
            screenDeviceAudioTrack = audioTrack;
            slog('SCREEN', 'system playback audio attached for screen share', {
              sessionId,
              trackId: audioTrack.id,
            });
            flowLog('AUDIO_FLOW', 'PLAYBACK_CAPTURE_STARTED', {sessionId, trackId: audioTrack.id});
          }
        }
      } catch (e) {
        slog('SCREEN', 'device audio capture skipped', String(e));
      }
    }

    streamStateTransition('CAPTURE_RUNNING', 'connectChildStreamImpl', {
      streamType,
      tracks: stream.getTracks().map(t => ({kind: t.kind, id: t.id, readyState: t.readyState})),
    });

    activeMediaStream = stream;
    
    // Create peer connection with improved error handling
    try {
      pc = new RTCPeerConnection({
        iceServers: ice.iceServers,
        iceTransportPolicy: ice.iceTransportPolicy,
        bundlePolicy: 'max-bundle',
        iceGatheringPolicy: 'gather_continually',
        rtcpMuxPolicy: 'require',
      });
      slog('PC', 'PeerConnection created successfully', {
        iceServers: ice.iceServers.length,
        policy: ice.iceTransportPolicy
      });
    } catch (pcError) {
      slog('PC', 'PeerConnection creation failed', String(pcError));
      throw new Error(`Failed to create PeerConnection: ${String(pcError)}`);
    }
    attachConnectionHandlers(pc);

    pc.onicecandidate = ev => {
      if (!ev.candidate) {
        slog('ICE', 'gathering complete');
        logIce('child', 'ICE gathering complete');
        return;
      }
      slog('ICE', 'local candidate', ev.candidate.candidate?.slice(0, 60));
      logIce('child', 'local ICE', ev.candidate.candidate?.slice(0, 40));
      signalingApi.post(roomId, 'ice', JSON.stringify(ev.candidate.toJSON()), sessionId).catch(err => {
        slog('ICE', 'post failed', String(err));
      });
    };

    stream.getTracks().forEach(track => {
      pc?.addTrack(track, stream);
      slog('PC', 'addTrack', {kind: track.kind, id: track.id, enabled: track.enabled});
      if (track.kind === 'video') {
        flowLog('MEDIA_PROJECTION', 'VIDEO_TRACK_ADDED', {
          trackId: track.id,
          enabled: track.enabled,
          readyState: track.readyState,
        });
      } else if (track.kind === 'audio') {
        slog('PC', 'AUDIO TRACK added to PeerConnection', {
          trackId: track.id,
          enabled: track.enabled,
          readyState: track.readyState,
          muted: track.muted,
          streamType: streamType
        });
        flowLog('AUDIO_FLOW', 'AUDIO_TRACK_ADDED_TO_PC', {
          sessionId,
          trackId: track.id,
          enabled: track.enabled,
          readyState: track.readyState,
          streamType: streamType
        });
      }
    });

    // Camera is video-only — no ambient mic track on the peer connection.
    if (streamType === 'SCREEN' && screenDeviceAudioTrack) {
      pc?.addTrack(screenDeviceAudioTrack, stream);
      slog('PC', 'addTrack screen device audio', {kind: 'audio', id: screenDeviceAudioTrack.id});
      flowLog('AUDIO_FLOW', 'PLAYBACK_FRAME_SENT', {sessionId, trackId: screenDeviceAudioTrack.id});
    }

    const offerLabel = streamType === 'CAMERA' ? 'CAMERA_FLOW' : 'SCREEN_SHARE_FLOW';
    flowLog(offerLabel, 'OFFER_CREATING', {roomId, sessionId, streamType});
    
    let offer: RTCSessionDescriptionInit;
    try {
      offer = await Promise.race([
        pc.createOffer({offerToReceiveAudio: false, offerToReceiveVideo: false}),
        new Promise<never>((_, reject) => 
          setTimeout(() => reject(new Error('Offer creation timeout')), 10000)
        )
      ]);
      slog('SDP', 'Offer created successfully', {roomId, sessionId});
    } catch (offerError) {
      slog('SDP', 'Offer creation failed', {error: String(offerError), roomId, sessionId});
      throw new Error(`Failed to create offer: ${String(offerError)}`);
    }
    
    const offerHasVideo = sdpHasVideoMline(offer.sdp);
    flowLog(offerLabel, 'OFFER_CREATED', {roomId, sessionId, streamType, hasVideo: offerHasVideo});
    flowLog('WEBRTC_FLOW', offerHasVideo ? 'OFFER_CONTAINS_VIDEO' : 'OFFER_MISSING_VIDEO', {
      roomId,
      sessionId,
    });
    if (streamType === 'CAMERA' && !offerHasVideo) {
      slog('SDP', 'WARN camera offer has no video m-line — frames may not be flowing yet', {roomId, sessionId});
    }
    try {
      await pc.setLocalDescription(offer);
      slog('SDP', 'Local description set successfully', {roomId, sessionId});
      streamStateTransition('LOCAL_DESCRIPTION_SET', 'connectChildStreamImpl', {roomId, sessionId});
    } catch (localDescError) {
      slog('SDP', 'Failed to set local description', {error: String(localDescError), roomId, sessionId});
      throw new Error(`Failed to set local description: ${String(localDescError)}`);
    }
    
    try {
      await Promise.race([
        postChildOffer(roomId, sessionId, offer, 'connect'),
        new Promise<never>((_, reject) =>
          setTimeout(() => reject(new Error('Signaling API timeout')), 15000),
        ),
      ]);
      slog('SDP', 'offer sent', roomId);
    } catch (signalingError) {
      if (isStreamingSessionEndedError(signalingError)) {
        throw signalingError;
      }
      // postChildOffer already wraps hard failures; timeout lands here.
      if (
        signalingError instanceof Error &&
        signalingError.message.startsWith('Failed to send offer:')
      ) {
        throw signalingError;
      }
      slog('SDP', 'Failed to send offer via signaling', {
        error: String(signalingError),
        roomId,
        sessionId,
      });
      throw new Error(`Failed to send offer: ${String(signalingError)}`);
    }
    if (streamType === 'CAMERA') {
      flowLog('CAMERA_FLOW', 'OFFER_SENT', {roomId, sessionId, hasVideo: offerHasVideo});
    } else {
      flowLog('SCREEN_SHARE_FLOW', 'offer sent', {roomId, sessionId});
    }
    logIce('child', 'offer sent');

    pollTimer = setInterval(() => {
      pollSignaling(roomId).catch(() => undefined);
    }, 500);

    const answerOk = await waitForRemoteAnswer(roomId);
    if (streamType === 'CAMERA') {
      flowLog('CAMERA_FLOW', answerOk ? 'ANSWER_RECEIVED' : 'ANSWER_TIMEOUT', {roomId, sessionId});
    }
    // W3 fix: stop the 500 ms poll once the answer is in hand.
    // Continuing to poll throughout the session caused a DB read storm:
    // 2 polls/sec × both sides × N sessions = thousands of unnecessary reads.
    // A slow-poll (5 s) is restarted only during ICE recovery phases via attachIceRecovery.
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
    if (!answerOk) {
      throw new Error('Couldn’t connect to the parent phone. Ask them to stay on live view and try again.');
    }
    startIceMonitor('child post-negotiation');
    streamStateTransition('STREAMING', 'connectChildStreamImpl', {roomId, sessionId});
    markJsActive();
    void nativeModules.markJsStreamSessionActive(sessionId).catch(() => undefined);
    
    // Reset connection attempts on successful connection
    connectionAttempts = 0;
    
    slog('PC', 'negotiation complete', {
      ice: pc?.iceConnectionState,
      connection: pc?.connectionState,
    });
  } catch (err) {
    streamStateTransition('FAILED', 'connectChildStreamImpl', {error: String(err)});
    slog('FLOW', 'connectChildStreamImpl FAILED', String(err));
    throw err;
  } finally {
    connecting = false;
    slog('FLOW', 'connectChildStreamImpl END', {roomId, ice: pc?.iceConnectionState});
  }
}

function stopTrackSafely(track: MediaStreamTrack, label: string): void {
  try {
    track.stop();
    slog('PC', 'track stopped', {kind: track.kind, id: track.id, label});
  } catch (err) {
    slog('PC', 'track stop skipped', {kind: track.kind, label, err: String(err)});
  }
}

// Network quality monitoring for proactive connection management
function startNetworkQualityMonitoring(peer: RTCPeerConnection): () => void {
  let qualityInterval: ReturnType<typeof setInterval> | null = null;
  let lastStats: RTCStatsReport | null = null;
  
  const checkNetworkQuality = async () => {
    if (!peer || peer.connectionState === 'closed') return;
    
    try {
      const stats = await peer.getStats();
      
      if (lastStats) {
        // Analyze connection quality trends
        let totalPacketsLost = 0;
        let totalPacketsSent = 0;
        let roundTripTime = 0;
        let jitter = 0;
        
        stats.forEach(report => {
          if (report.type === 'outbound-rtp') {
            totalPacketsLost += report.packetsLost || 0;
            totalPacketsSent += report.packetsSent || 0;
          } else if (report.type === 'candidate-pair' && report.state === 'succeeded') {
            roundTripTime = report.currentRoundTripTime || 0;
          } else if (report.type === 'inbound-rtp') {
            jitter = report.jitter || 0;
          }
        });
        
        const lossRate = totalPacketsSent > 0 ? totalPacketsLost / totalPacketsSent : 0;
        const qualityIssues = lossRate > 0.05 || roundTripTime > 0.5 || jitter > 0.1;
        
        if (qualityIssues) {
          slog('PC', 'Network quality degradation detected', {
            lossRate: Math.round(lossRate * 100) / 100,
            roundTripTime: Math.round(roundTripTime * 1000),
            jitter: Math.round(jitter * 1000),
            roomId: activeRoomId,
            sessionId: activeSessionId
          });
        }
      }
      
      lastStats = stats;
    } catch (error) {
      // Ignore stats errors - they're not critical
    }
  };
  
  // Check quality every 10 seconds
  qualityInterval = setInterval(checkNetworkQuality, 10000);

  return () => {
    if (qualityInterval) {
      clearInterval(qualityInterval);
      qualityInterval = null;
    }
  };
}

export async function disconnectStream(clearConnecting = true): Promise<void> {
  if (activeStreamType === 'CAMERA') {
    flowLog('CAMERA_FLOW', 'STREAM_STOPPED', {room: activeRoomId, sessionId: activeSessionId});
  }
  slog('FLOW', 'disconnectStream', {room: activeRoomId, clearConnecting});
  detachIceRecovery?.();
  detachIceRecovery = null;
  detachNetworkQualityMonitor?.();
  detachNetworkQualityMonitor = null;
  void nativeModules.logStreamLifecycle('PEER_CONNECTION_CLOSED', activeRoomId ?? 'no-room');
  stopIceMonitor();
  const room = activeRoomId;
  const stream = activeMediaStream;
  const conn = pc;
  const streamTypeToStop = activeStreamType;
  const hadActiveStream = stream != null || conn != null;
  activeRoomId = null;
  activeSessionId = null;
  activeMediaStream = null;
  pc = null;
  if (room) {
    unsubscribeSignalingRoom(room);
  }
  if (clearConnecting) {
    connecting = false;
    displayMediaPending = false;
    setCapturePromptActive(false);
  }
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  if (conn) {
    slog('PC', 'closing peer connection');
    try {
      conn.close();
    } catch (err) {
      slog('PC', 'close failed', String(err));
    }
  }
  // Only tear down capture pipelines that were actually streaming — not pre-warmed FGS
  // from MainActivity (stopping audio mid-start causes ForegroundServiceDidNotStartInTimeException).
  if (hadActiveStream) {
    if (streamTypeToStop === 'CAMERA') {
      await nativeModules.stopHiddenCameraStream().catch(() => undefined);
      
      // Release forced camera access
      try {
        await nativeModules.releaseCameraAccess();
        slog('CAMERA', 'Released forced camera access');
      } catch (error) {
        slog('CAMERA', `Failed to release camera access: ${error}`);
      }
      
      // Camera sessions also capture microphone — stop that too.
      if (cameraAudioStream) {
        await nativeModules.stopHiddenAudioStream().catch(() => undefined);
        cameraAudioStream = null;
      }
      cameraAudioTrack = null;
    } else if (streamTypeToStop === 'AUDIO') {
      await nativeModules.stopHiddenAudioStream().catch(() => undefined);
    } else {
      // Always stop playback audio with screen — track attach may have failed while
      // DevicePlaybackCaptureSession was still running.
      await nativeModules.stopHiddenScreenStream().catch(() => undefined);
      await nativeModules.stopHiddenAudioStream().catch(() => undefined);
      screenDeviceAudioTrack = null;
    }
  }
  if (stream && Platform.OS !== 'android') {
    stream.getTracks().forEach(track => stopTrackSafely(track, 'stream'));
  } else if (stream) {
    slog('PC', 'hidden capture tracks released natively', {count: stream.getTracks().length});
  }
  pendingIceCandidates.length = 0;
  if (Platform.OS === 'android') {
    await nativeModules.setMonitoringAudioMode(false).catch(() => undefined);
    await nativeModules.releaseJsStreamSession().catch(() => undefined);
  }
  endSession();
  slog('PC', 'disconnected');
}

export async function switchCamera(): Promise<void> {
  if (cameraSwitchInFlight) {
    slog('CAMERA', 'switch skipped — already in progress');
    return;
  }
  const videoTrack = activeMediaStream?.getVideoTracks()[0];
  if (!videoTrack || !pc || !activeMediaStream) {
    slog('CAMERA', 'switch skipped — no active camera stream');
    return;
  }

  cameraSwitchInFlight = true;
  const switchWatchdog = setTimeout(() => {
    if (cameraSwitchInFlight) {
      slog('CAMERA', 'switch watchdog — clearing stuck in-flight lock');
      cameraSwitchInFlight = false;
    }
  }, 8_000);

  try {
    // Always sync JS facing from the live CameraX capturer before flipping.
    // Stale JS state caused no-op switches after the first flip.
    if (Platform.OS === 'android') {
      try {
        const nativeFacing = await nativeModules.getCurrentCameraFacing();
        if (nativeFacing === 'user' || nativeFacing === 'environment') {
          currentCameraFacing = nativeFacing;
        }
      } catch {
        // Keep last known facing if native query fails.
      }
    }

    const fromFacing = currentCameraFacing;
    const nextFacing = fromFacing === 'environment' ? 'user' : 'environment';
    slog('CAMERA', 'switching camera', {from: fromFacing, to: nextFacing, trackId: videoTrack.id});

    if (Platform.OS === 'android') {
      const result = await nativeModules.switchHiddenCameraFacing(nextFacing);
      const actualFacing =
        result?.facingMode === 'environment'
          ? 'environment'
          : result?.facingMode === 'user'
            ? 'user'
            : nextFacing;
      currentCameraFacing = actualFacing;

      // If native reported a no-op (already on requested facing), force the other side once.
      if (result?.noop === true || result?.switchSuccessful === false || actualFacing === fromFacing) {
        const forced = actualFacing === 'environment' ? 'user' : 'environment';
        slog('CAMERA', 'native switch no-op — forcing opposite', {from: actualFacing, to: forced});
        const forcedResult = await nativeModules.switchHiddenCameraFacing(forced);
        if (forcedResult?.facingMode === 'environment' || forcedResult?.facingMode === 'user') {
          currentCameraFacing = forcedResult.facingMode;
        } else {
          currentCameraFacing = forced;
        }
      }

      slog('CAMERA', 'native switch done', {
        from: fromFacing,
        to: currentCameraFacing,
        switchSuccessful: result?.switchSuccessful,
      });
      return;
    }

    // Non-Android fallback: recreate via getUserMedia + replaceTrack.
    const newStream = await mediaDevices.getUserMedia({
      audio: false,
      video: {facingMode: nextFacing},
    });
    const newTrack = newStream.getVideoTracks()[0];
    if (!newTrack) {
      newStream.getTracks().forEach(t => t.stop());
      throw new Error('Couldn’t switch cameras. Please try again.');
    }
    const sender = pc.getSenders().find(s => s.track?.kind === 'video');
    if (sender) {
      await sender.replaceTrack(newTrack);
    } else {
      pc.addTrack(newTrack, activeMediaStream);
    }
    videoTrack.stop();
    activeMediaStream.removeTrack(videoTrack);
    activeMediaStream.addTrack(newTrack);
    currentCameraFacing = nextFacing;
    slog('CAMERA', 'switched via replaceTrack', {facing: nextFacing});
  } catch (err) {
    slog('CAMERA', 'switch failed', String(err));
  } finally {
    clearTimeout(switchWatchdog);
    cameraSwitchInFlight = false;
  }
}

/**
 * Get current camera facing mode for debugging.
 * Helps diagnose state sync issues between JS and native layers.
 */
export function getCurrentCameraFacing(): 'user' | 'environment' {
  return currentCameraFacing;
}

/**
 * Force reset camera facing state.
 * Recovery mechanism for stuck camera states - use sparingly.
 */
export function resetCameraFacing(facing: 'user' | 'environment'): void {
  slog('CAMERA', 'force reset facing', {from: currentCameraFacing, to: facing});
  currentCameraFacing = facing;
}

/**
 * Enhanced camera switch with improved state management and error recovery.
 * Attempts multiple methods to ensure bidirectional switching works reliably.
 */
export async function switchCameraWithRetry(maxRetries: number = 2): Promise<boolean> {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      slog('CAMERA', 'switch attempt', {attempt, maxRetries, currentFacing: currentCameraFacing});
      await switchCamera();
      slog('CAMERA', 'switch attempt successful', {attempt, newFacing: currentCameraFacing});
      return true;
    } catch (error) {
      slog('CAMERA', 'switch attempt failed', {attempt, error: String(error)});
      if (attempt === maxRetries) {
        slog('CAMERA', 'all switch attempts exhausted', {maxRetries});
        return false;
      }
      // Wait before retry to allow any ongoing operations to complete
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  }
  return false;
}
