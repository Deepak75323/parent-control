import {
  RTCPeerConnection,
  RTCSessionDescription,
  RTCIceCandidate,
  MediaStream,
} from 'react-native-webrtc';
import {monitoringApi, signalingApi} from './api';
import {slog, flowLog} from './streamDebug';
import {subscribeSignalingRoom, unsubscribeSignalingRoom} from './signalingWebSocket';
import {
  adaptIceServersForDev,
  defaultIceConfig,
  isIceConfigStale,
  logIce,
  orderedSignalingMessages,
} from './webrtcIce';
import {config} from '../utils/config';
import {nativeModules} from '../nativeModules';
import {
  beginSession,
  emitConnectionState,
  emitRemoteStream,
  endSession,
  setSessionPhase,
} from './streamSessionOwner';
import {attachIceRecovery} from './iceRecovery';

export type SignalingPayload = {
  id?: string;
  messageType: string;
  payload: string;
  senderRole: string;
};

let pc: RTCPeerConnection | null = null;
let remoteStream: MediaStream | null = null;
let pollTimer: ReturnType<typeof setInterval> | null = null;
let activeRoomId: string | null = null;
let activeSessionId: string | undefined;
let cachedIceConfig: ReturnType<typeof defaultIceConfig> | null = null;
const seenIds = new Set<string>();
const pendingIceCandidates: RTCIceCandidateInit[] = [];
let detachIceRecovery: (() => void) | null = null;
let viewerAudioModeActive = false;

export {registerStreamListeners, setOnConnectionState, setOnRemoteStream} from './streamSessionOwner';

async function enableViewerAudioPlayback(): Promise<void> {
  if (viewerAudioModeActive) return;
  await nativeModules.setViewerAudioMode(true).catch(() => undefined);
  viewerAudioModeActive = true;
}

async function disableViewerAudioPlayback(): Promise<void> {
  if (!viewerAudioModeActive) return;
  await nativeModules.setViewerAudioMode(false).catch(() => undefined);
  viewerAudioModeActive = false;
}

function parseSessionDescription(payload: string): RTCSessionDescriptionInit {
  try {
    return JSON.parse(payload) as RTCSessionDescriptionInit;
  } catch {
    return {type: 'offer', sdp: payload};
  }
}

async function loadIceConfig(
  sessionId?: string,
): Promise<ReturnType<typeof defaultIceConfig>> {
  const apiServers: RTCIceServer[] = [];
  let credentialExpiresAt: number | undefined;
  try {
    // P2-4: bind TURN credentials to the active streaming session when known so a leak
    // is limited to relaying this single session (≤15 min lifetime).
    const {data} = await monitoringApi.webrtcConfig(sessionId);
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
    adaptIceServersForDev(apiServers, config.apiBaseUrl),
    credentialExpiresAt,
  );
  slog('ICE', 'config loaded', {
    servers: ice.iceServers.length,
    policy: ice.iceTransportPolicy,
    expiresAt: ice.credentialExpiresAt,
  });
  return ice;
}

async function ensureIceConfig(
  sessionId?: string,
): Promise<ReturnType<typeof defaultIceConfig>> {
  // Re-fetch on session change so credentials carry the new sessionId, not just on TTL expiry.
  const wantsSessionBound = !!sessionId;
  const cachedIsSessionBound = !!cachedIceConfig?.sessionId;
  const sessionChanged =
    wantsSessionBound !== cachedIsSessionBound ||
    (wantsSessionBound && cachedIceConfig?.sessionId !== sessionId);
  if (!cachedIceConfig || isIceConfigStale(cachedIceConfig) || sessionChanged) {
    cachedIceConfig = await loadIceConfig(sessionId);
    cachedIceConfig = {...cachedIceConfig, sessionId};
  }
  return cachedIceConfig;
}

/** P2-1: derive streamType from the active room ID (`screen_…`, `camera_…`, `audio_…`). */
function streamTypeFromRoom(roomId: string | null): 'SCREEN' | 'CAMERA' | 'AUDIO' {
  if (roomId?.startsWith('camera_')) return 'CAMERA';
  if (roomId?.startsWith('audio_')) return 'AUDIO';
  return 'SCREEN';
}

function reportIceTelemetry(
  iceConnectionState: string,
  streamType: 'SCREEN' | 'CAMERA' | 'AUDIO' = streamTypeFromRoom(activeRoomId),
  failureReason?: string,
): void {
  monitoringApi
    .reportIceConnectivity({
      streamType,
      sessionId: activeSessionId,
      iceConnectionState,
      turnUsed: undefined,
      failureReason,
    })
    .catch(() => undefined);
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

function sdpHasVideoMline(sdp: string | undefined): boolean {
  return !!sdp && /^m=video /m.test(sdp);
}

async function applyRemoteOffer(payload: string, messageId: string): Promise<boolean> {
  if (!pc || seenIds.has(messageId)) return false;

  // Child restarted negotiation (e.g. after hidden capture retry) — accept the new offer.
  if (
    pc.remoteDescription?.type === 'offer' &&
    pc.localDescription?.type === 'answer'
  ) {
    slog('SDP', 'child re-offer — resetting peer connection', messageId);
    seenIds.clear();
    pendingIceCandidates.length = 0;
    remoteStream = null;
    const roomId = activeRoomId;
    const sessionId = activeSessionId;
    const ice = cachedIceConfig ?? (await loadIceConfig());
    if (pc) {
      pc.close();
      pc = null;
    }
    if (!roomId) return false;
    createPeerConnection(ice, roomId, sessionId);
  }

  try {
    await pc!.setRemoteDescription(new RTCSessionDescription(parseSessionDescription(payload)));
    seenIds.add(messageId);
    slog('SDP', 'remote OFFER applied', messageId);
    flowLog('WEBRTC_FLOW', sdpHasVideoMline(pc!.remoteDescription?.sdp) ? 'OFFER_CONTAINS_VIDEO' : 'OFFER_MISSING_VIDEO', {
      roomId: activeRoomId,
    });
    slog('FLOW', 'child offer received', {messageId, roomId: activeRoomId});
    logIce('parent', 'remote offer applied');
    await flushPendingIceCandidates();

    const answer = await pc!.createAnswer();
    await pc!.setLocalDescription(answer);
    flowLog('WEBRTC_FLOW', sdpHasVideoMline(answer.sdp) ? 'ANSWER_CONTAINS_VIDEO' : 'ANSWER_MISSING_VIDEO', {
      roomId: activeRoomId,
    });
    if (activeRoomId) {
      await signalingApi.post(activeRoomId, 'answer', JSON.stringify(answer), activeSessionId);
    }
    slog('SDP', 'answer sent', activeRoomId);
    logIce('parent', 'answer sent');
    return true;
  } catch (err) {
    slog('SDP', 'remote OFFER failed', String(err));
    logIce('parent', 'remote offer failed', String(err));
    return false;
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
    logIce('parent', 'ICE applied', candidate.candidate?.slice(0, 40));
  } catch (err) {
    slog('ICE', 'candidate failed', String(err));
    logIce('parent', 'ICE failed', String(err));
  }
}

async function handleSignalingMessage(msg: SignalingPayload): Promise<void> {
  if (!pc) return;
  if (msg.senderRole === 'PARENT') return;
  const messageId = msg.id ?? `${msg.messageType}-${msg.payload.slice(0, 32)}`;
  slog('SIGNAL', 'incoming message', {
    type: msg.messageType,
    from: msg.senderRole,
    id: messageId,
  });

  if (msg.messageType === 'offer') {
    await applyRemoteOffer(msg.payload, messageId);
    return;
  }
  if (msg.messageType === 'ice') {
    await applyRemoteIce(msg.payload, messageId);
  }
}

async function pollSignaling(): Promise<boolean> {
  if (!activeRoomId) return false;
  try {
    const {data} = await signalingApi.recent(activeRoomId);
    const messages = orderedSignalingMessages(data.data ?? []);
    slog('POLL', `got ${messages.length} messages`, activeRoomId);

    let latestOffer: SignalingPayload | null = null;
    for (const msg of messages) {
      const payload: SignalingPayload = {
        id: msg.id,
        messageType: msg.messageType,
        payload: msg.payload,
        senderRole: msg.senderRole,
      };
      if (payload.messageType === 'offer' && payload.senderRole !== 'PARENT') {
        latestOffer = payload;
        continue;
      }
      await handleSignalingMessage(payload);
    }

    if (latestOffer) {
      const messageId =
        latestOffer.id ?? `${latestOffer.messageType}-${latestOffer.payload.slice(0, 32)}`;
      await applyRemoteOffer(latestOffer.payload, messageId);
    }

    return pc?.remoteDescription?.type === 'offer';
  } catch (err) {
    slog('POLL', 'signaling fetch failed', String(err));
    logIce('parent', 'signaling poll error', String(err));
    return false;
  }
}

async function waitForRemoteOffer(timeoutMs = 60000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline && pc && !pc.remoteDescription) {
    const applied = await pollSignaling();
    if (applied || pc?.remoteDescription) {
      slog('SDP', 'offer received during wait');
      return true;
    }
    await new Promise<void>(resolve => setTimeout(resolve, 300));
  }
  const ok = pc?.remoteDescription?.type === 'offer';
  if (!ok) {
    slog('SDP', 'offer wait timed out', {ice: pc?.iceConnectionState});
  }
  return ok;
}

async function refreshIceAndRestart(): Promise<void> {
  cachedIceConfig = await loadIceConfig();
}

async function renegotiateParentViewer(): Promise<void> {
  if (!activeRoomId) return;
  slog('ICE', 'parent full reconnect after ICE recovery exhausted');
  await reconnectParentViewer();
}

function attachConnectionHandlers(peer: RTCPeerConnection): void {
  detachIceRecovery?.();
  detachIceRecovery = attachIceRecovery(peer, {
    onRestartIce: refreshIceAndRestart,
    onRenegotiate: renegotiateParentViewer,
    onFatal: reason => {
      reportIceTelemetry(peer.iceConnectionState ?? 'failed', 'SCREEN', reason);
      setSessionPhase('TEARDOWN');
    },
    reportEvent: (event, attrs) => {
      slog('ICE', event, attrs ?? {});
      monitoringApi
        .reportIceConnectivity({
          streamType: 'SCREEN',
          sessionId: activeSessionId,
          iceConnectionState: peer.iceConnectionState ?? 'unknown',
          failureReason: event === 'ice_recovery_failed' ? 'recovery_exhausted' : undefined,
        })
        .catch(() => undefined);
    },
  });

  const emitIceState = () => {
    const state = peer.iceConnectionState ?? 'unknown';
    slog('PC', 'iceConnectionState', state);
    logIce('parent', 'iceConnectionState', state);
    emitConnectionState(state);
    if (state === 'connected' || state === 'completed') {
      setSessionPhase('ACTIVE');
      reportIceTelemetry(state);
    } else if (state === 'disconnected' || state === 'failed') {
      setSessionPhase('RECONNECTING');
    }
  };
  peer.oniceconnectionstatechange = emitIceState;
  peer.onconnectionstatechange = () => {
    slog('PC', 'connectionState', peer.connectionState ?? 'unknown');
    logIce('parent', 'connectionState', peer.connectionState ?? 'unknown');
  };
  peer.onicegatheringstatechange = () => {
    slog('PC', 'iceGatheringState', peer.iceGatheringState ?? 'unknown');
  };
  emitIceState();
}

async function waitForIceConnected(timeoutMs = 60000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  let disconnectedSince: number | null = null;
  while (Date.now() < deadline && pc) {
    const state = pc.iceConnectionState;
    if (state === 'connected' || state === 'completed') {
      slog('PC', 'ICE connected', state);
      slog('FLOW', 'ICE connected', {state, roomId: activeRoomId});
      return true;
    }
    if (state === 'failed') {
      slog('PC', 'ICE failed', state);
      return false;
    }
    if (state === 'disconnected') {
      if (!disconnectedSince) {
        disconnectedSince = Date.now();
        slog('PC', 'ICE disconnected — grace period', {roomId: activeRoomId});
      } else if (Date.now() - disconnectedSince > 8000) {
        slog('PC', 'ICE disconnected timeout', state);
        return false;
      }
    } else {
      disconnectedSince = null;
    }
    await new Promise<void>(resolve => setTimeout(resolve, 250));
  }
  const state = pc?.iceConnectionState ?? 'unknown';
  slog('PC', 'ICE wait timed out', state);
  return state === 'connected' || state === 'completed';
}

function createPeerConnection(
  ice: ReturnType<typeof defaultIceConfig>,
  roomId: string,
  sessionId?: string,
): void {
  pc = new RTCPeerConnection({
    iceServers: ice.iceServers,
    iceTransportPolicy: ice.iceTransportPolicy,
    bundlePolicy: 'max-bundle',
  });
  attachConnectionHandlers(pc);

  pc.ontrack = ev => {
    if (ev.streams?.[0]) {
      remoteStream = ev.streams[0];
    } else if (ev.track) {
      remoteStream = remoteStream ?? new MediaStream();
      if (!remoteStream.getTracks().includes(ev.track)) {
        remoteStream.addTrack(ev.track);
      }
    }
    if (remoteStream) {
      if (ev.track?.kind === 'audio') {
        ev.track.enabled = true;
        enableViewerAudioPlayback().catch(() => undefined);
      }
      ev.track?.addEventListener('mute', () => slog('RENDER', 'track muted', ev.track?.kind));
      ev.track?.addEventListener('unmute', () => {
        slog('RENDER', 'track unmuted', ev.track?.kind);
        if (ev.track?.kind === 'video') {
          flowLog('WEBRTC_FLOW', 'FIRST_REMOTE_FRAME', {roomId: activeRoomId});
        }
        if (ev.track?.kind === 'audio') {
          enableViewerAudioPlayback().catch(() => undefined);
        }
      });
      const url = remoteStream.toURL();
      slog('RENDER', 'ontrack', {
        kind: ev.track?.kind,
        enabled: ev.track?.enabled,
        readyState: ev.track?.readyState,
        streamURL: url,
        tracks: remoteStream.getTracks().map(t => ({
          kind: t.kind,
          enabled: t.enabled,
          readyState: t.readyState,
        })),
      });
      if (ev.track?.kind === 'video') {
        flowLog('WEBRTC_FLOW', 'REMOTE_VIDEO_TRACK_RECEIVED', {
          roomId: activeRoomId,
          enabled: ev.track.enabled,
          readyState: ev.track.readyState,
          muted: ev.track.muted,
        });
      }
      logIce('parent', 'ontrack', `${ev.track?.kind ?? 'stream'} enabled=${ev.track?.enabled}`);
      emitRemoteStream(remoteStream);
    }
  };

  pc.onicecandidate = ev => {
    if (!ev.candidate) {
      slog('ICE', 'gathering complete');
      logIce('parent', 'ICE gathering complete');
      return;
    }
    slog('ICE', 'local candidate', ev.candidate.candidate?.slice(0, 60));
    logIce('parent', 'local ICE', ev.candidate.candidate?.slice(0, 40));
    signalingApi.post(roomId, 'ice', JSON.stringify(ev.candidate.toJSON()), sessionId).catch(err => {
      slog('ICE', 'post failed', String(err));
    });
  };
}

export async function connectParentViewer(roomId: string, sessionId?: string): Promise<void> {
  slog('FLOW', 'connectParentViewer START', {roomId, sessionId});
  await disconnectViewer();
  seenIds.clear();
  pendingIceCandidates.length = 0;
  activeRoomId = roomId;
  activeSessionId = sessionId;
  const correlationId = beginSession(roomId, sessionId);
  setSessionPhase('NEGOTIATING');
  slog('SESSION', 'correlation', {correlationId});

  const ice = await ensureIceConfig(sessionId);
  cachedIceConfig = ice;
  slog('PC', 'creating peer', {roomId, sessionId});
  logIce('parent', 'creating peer', `${ice.iceServers.length} ice servers`);

  createPeerConnection(ice, roomId, sessionId);

  subscribeSignalingRoom(roomId, payload => {
    handleSignalingMessage({
      id: payload.id as string | undefined,
      messageType: String(payload.messageType ?? ''),
      payload: String(payload.payload ?? ''),
      senderRole: String(payload.senderRole ?? ''),
    }).catch(err => slog('WS', 'handler error', String(err)));
  });

  pollTimer = setInterval(() => {
    pollSignaling().catch(() => undefined);
  }, 500);

  const offerOk = await waitForRemoteOffer();
  // W3 fix: stop the 500 ms poll once the remote offer is received.
  // Continuing to poll throughout the session caused a DB read storm.
  // ICE recovery phases restart a slow poll (5 s) via the recovery callbacks.
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  if (!offerOk) {
    const streamKind = roomId.startsWith('screen_')
      ? 'screen capture'
      : roomId.startsWith('audio_')
        ? 'microphone'
        : 'camera';
    slog('SDP', 'FATAL no child offer', {
      roomId,
      sessionId,
      iceState: pc?.iceConnectionState,
      remoteDesc: pc?.remoteDescription?.type ?? null,
    });
    throw new Error(
      `Child ${streamKind} did not start — check the child device is online and permissions are granted`,
    );
  }
  const iceOk = await waitForIceConnected();
  if (!iceOk) {
    throw new Error(
      'Media connection failed — run ./scripts/dev/dev-up.sh to start TURN, then retry',
    );
  }
  if (remoteStream?.getAudioTracks().length) {
    await enableViewerAudioPlayback();
  }
  slog('PC', 'negotiation complete', {
    ice: pc?.iceConnectionState,
    connection: pc?.connectionState,
  });
  setSessionPhase('ACTIVE');
  slog('FLOW', 'connectParentViewer END', {roomId, ice: pc?.iceConnectionState});
}

/** Re-run viewer negotiation on the active room (network handoff / transient ICE loss). */
export async function reconnectParentViewer(): Promise<void> {
  if (!activeRoomId) {
    throw new Error('No active stream to reconnect');
  }
  slog('FLOW', 'reconnectParentViewer', {roomId: activeRoomId, sessionId: activeSessionId});
  await connectParentViewer(activeRoomId, activeSessionId);
}

export async function disconnectViewer(): Promise<void> {
  await disableViewerAudioPlayback();
  detachIceRecovery?.();
  detachIceRecovery = null;
  if (activeRoomId) {
    unsubscribeSignalingRoom(activeRoomId);
    activeRoomId = null;
  }
  activeSessionId = undefined;
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  if (pc) {
    pc.close();
    pc = null;
  }
  remoteStream = null;
  pendingIceCandidates.length = 0;
  endSession();
  slog('PC', 'disconnected');
}

export function getRemoteStream(): MediaStream | null {
  return remoteStream;
}

export function getIceConnectionState(): string | null {
  return pc?.iceConnectionState ?? null;
}
