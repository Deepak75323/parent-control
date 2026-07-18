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
  if (pc.remoteDescription?.type === 'answer') {
    return true;
  }
  if (pc.localDescription?.type !== 'offer') {
    slog('SDP', 'answer skipped — no local offer yet', messageId);
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
  while (Date.now() < deadline && pc && !pc.remoteDescription) {
    const applied = await pollSignaling(roomId);
    if (applied || pc?.remoteDescription) {
      slog('SDP', 'answer received during wait');
      flowLog('SCREEN_SHARE_FLOW', 'answer received', {roomId});
      return true;
    }
    await new Promise<void>(resolve => setTimeout(resolve, 250));
  }
  const ok = pc?.remoteDescription?.type === 'answer';
  if (!ok) {
    slog('SDP', 'answer wait timed out', {roomId, ice: pc?.iceConnectionState});
  }
  return ok;
}

async function renegotiateChildOffer(): Promise<void> {
  if (!pc || !activeRoomId || !activeSessionId) return;
  setSessionPhase('RECONNECTING');
  void nativeModules.logStreamLifecycle('ICE_RENEGOTIATE', activeSessionId);
  const offer = await pc.createOffer({iceRestart: true});
  await pc.setLocalDescription(offer);
  await signalingApi.post(activeRoomId, 'offer', JSON.stringify(offer), activeSessionId);
  slog('ICE', 'ice-restart offer sent', {roomId: activeRoomId});
}

function attachConnectionHandlers(peer: RTCPeerConnection): void {
  detachIceRecovery?.();
  detachIceRecovery = attachIceRecovery(peer, {
    onRestartIce: async () => {
      void nativeModules.logStreamLifecycle('ICE_RESTART', activeSessionId ?? 'no-session');
      StreamSessionRegistryMarkReconnecting();
    },
    onRenegotiate: renegotiateChildOffer,
    onFatal: reason => {
      slog('PC', 'ICE recovery exhausted', {reason, roomId: activeRoomId});
      if (activeSessionId) {
        deviceApi.reportStreamFailure(activeSessionId, reason).catch(() => undefined);
      }
    },
    reportEvent: (event, attrs) => {
      slog('ICE', event, attrs ?? {});
      void nativeModules.logStreamLifecycle(`ICE_${event.toUpperCase()}`, JSON.stringify(attrs ?? {}));
      streamingApi
        .reportIceConnectivity({
          streamType: activeStreamType,
          sessionId: activeSessionId ?? undefined,
          iceConnectionState: peer.iceConnectionState ?? 'unknown',
          failureReason: event === 'ice_recovery_failed' ? 'recovery_exhausted' : undefined,
        })
        .catch(() => undefined);
    },
  });

    peer.oniceconnectionstatechange = () => {
      const state = peer.iceConnectionState ?? 'unknown';
      slog('PC', 'iceConnectionState', state);
      logIce('child', 'iceConnectionState', state);
      if (state === 'connected' || state === 'completed') {
        streamStateTransition('CONNECTED', 'oniceconnectionstatechange', {state, roomId: activeRoomId});
        markJsActive();
        flowLog('WEBRTC_FLOW', 'ICE_CONNECTED', {state, roomId: activeRoomId});
        if (activeStreamType === 'CAMERA') {
          flowLog('CAMERA_FLOW', 'ICE_CONNECTED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        }
        streamingApi
          .reportIceConnectivity({
            streamType: activeStreamType,
            sessionId: activeSessionId ?? undefined,
            iceConnectionState: state,
          })
          .catch(() => undefined);
      } else if (state === 'failed' || state === 'disconnected') {
        if (activeStreamType === 'CAMERA') {
          flowLog('CAMERA_FLOW', 'ICE_STATE_CHANGED', {state, roomId: activeRoomId, sessionId: activeSessionId});
        }
      }
    };
  peer.onconnectionstatechange = () => {
    slog('PC', 'connectionState', peer.connectionState ?? 'unknown');
    logIce('child', 'connectionState', peer.connectionState ?? 'unknown');
  };
  peer.onicegatheringstatechange = () => {
    const state = peer.iceGatheringState ?? 'unknown';
    if (state === 'gathering') {
      streamStateTransition('ICE_CONNECTING', 'onicegatheringstatechange', {state});
    }
    slog('PC', 'iceGatheringState', state);
  };
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
  if (isStreamingSession(roomId, sessionId)) {
    slog('PC', 'already streaming session — skip', {roomId, sessionId});
    return;
  }
  if (connecting) {
    slog('PC', 'connect already in progress — skip duplicate', {roomId, sessionId});
    return;
  }

  connecting = true;
  streamStateTransition('WEBRTC_CONNECTING', 'connectChildStream', {roomId, streamType, sessionId});
  slog('FLOW', 'connectChildStream START', {roomId, streamType, sessionId});
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
    streamStateTransition('PERMISSION_VERIFIED', 'connectChildStream', {streamType});

    let stream: MediaStream;
    if (streamType === 'AUDIO') {
      // Device / system audio (YouTube, Spotify, games) via MediaProjection AudioPlaybackCapture.
      // Do NOT set MODE_IN_COMMUNICATION or exclusive audio focus — that freezes other apps' A/V.
      flowLog('PERMISSION_FLOW', 'device playback capture — AudioPlaybackCapture', {sessionId});
      const playbackOk = await nativeModules.startDevicePlaybackCapture().catch((e: unknown) => {
        slog('AUDIO', 'PLAYBACK_CAPTURE_FAILED', String(e));
        return false;
      });
      if (!playbackOk) {
        throw new Error(
          'Listening couldn’t start. Open Clock Time on the child phone, allow screen sharing if asked, then try again.',
        );
      }
      const data = await nativeModules.getHiddenPlaybackAudioMediaStream();
      stream = new MediaStream({
        streamId: data.streamId,
        streamReactTag: data.streamId,
        tracks: [data.track],
      } as {streamId: string; streamReactTag: string; tracks: object[]});
      flowLog('AUDIO_FLOW', 'PLAYBACK_CAPTURE_STARTED', {sessionId, streamId: data.streamId});
    } else if (streamType === 'CAMERA') {
      flowLog('CAMERA_FLOW', 'CAMERA_COMMAND_RECEIVED', {sessionId, facing: cameraFacing});
      const facing = cameraFacing === 'environment' ? 'environment' : 'user';
      currentCameraFacing = facing;
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

      // Capture ambient room mic alongside the camera (FlashGet Kids-style).
      // Do NOT use MODE_IN_COMMUNICATION or exclusive AUDIOFOCUS_GAIN — that pauses
      // YouTube/Spotify on the child while the parent is only monitoring.
      cameraAudioTrack = null;
      try {
        await nativeModules.setMonitoringAudioMode(false).catch(() => undefined);
        flowLog('CAMERA_FLOW', 'AMBIENT_MIC_CAPTURE_STARTED', {sessionId});
        const audioData = await nativeModules.getHiddenAmbientAudioMediaStream();
        cameraAudioStream = new MediaStream({
          streamId: audioData.streamId,
          streamReactTag: audioData.streamId,
          tracks: [audioData.track],
        } as {streamId: string; streamReactTag: string; tracks: object[]});
        const audioTrack = cameraAudioStream.getAudioTracks()[0];
        if (audioTrack) {
          cameraAudioTrack = audioTrack;
          slog('CAMERA', 'ambient mic track captured, will add to PC after creation', {trackId: audioTrack.id});
          flowLog('CAMERA_FLOW', 'MIC_TRACK_CAPTURED', {sessionId, trackId: audioTrack.id, ambient: true});
          flowLog('CAMERA_FLOW', 'MIC_PCM_GENERATED', {sessionId, trackId: audioTrack.id});
        } else {
          throw new Error('Microphone wasn’t available. Close other apps and try again.');
        }
      } catch (audioErr) {
        slog('CAMERA', 'ambient mic capture failed — retrying once', String(audioErr));
        try {
          await nativeModules.setMonitoringAudioMode(false).catch(() => undefined);
          const audioData = await nativeModules.getHiddenAmbientAudioMediaStream();
          cameraAudioStream = new MediaStream({
            streamId: audioData.streamId,
            streamReactTag: audioData.streamId,
            tracks: [audioData.track],
          } as {streamId: string; streamReactTag: string; tracks: object[]});
          const audioTrack = cameraAudioStream.getAudioTracks()[0];
          if (!audioTrack) throw audioErr;
          cameraAudioTrack = audioTrack;
          flowLog('CAMERA_FLOW', 'MIC_TRACK_CAPTURED', {sessionId, trackId: audioTrack.id, retry: true, ambient: true});
        } catch (retryErr) {
          slog('CAMERA', 'ambient mic capture failed after retry — continuing video-only', String(retryErr));
          cameraAudioStream = null;
          cameraAudioTrack = null;
        }
      }
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
            screenDeviceAudioTrack = audioTrack;
            flowLog('AUDIO_FLOW', 'PLAYBACK_CAPTURE_STARTED', {sessionId, trackId: audioTrack.id});
          }
        }
      } catch (e) {
        slog('SCREEN', 'device audio capture skipped', String(e));
      }
    }

    streamStateTransition('CAPTURE_RUNNING', 'connectChildStream', {
      streamType,
      tracks: stream.getTracks().map(t => ({kind: t.kind, id: t.id, readyState: t.readyState})),
    });

    activeMediaStream = stream;
    pc = new RTCPeerConnection({
      iceServers: ice.iceServers,
      iceTransportPolicy: ice.iceTransportPolicy,
      bundlePolicy: 'max-bundle',
    });
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
      }
    });

    // For CAMERA streams: add the audio track directly to the PC (not via stream.addTrack,
    // which crashes when the track originates from a different native MediaStream).
    if (streamType === 'CAMERA' && cameraAudioTrack) {
      pc?.addTrack(cameraAudioTrack, stream);
      slog('PC', 'addTrack camera mic', {kind: 'audio', id: cameraAudioTrack.id});
      flowLog('CAMERA_FLOW', 'MIC_TRACK_ADDED_TO_PC', {sessionId, trackId: cameraAudioTrack.id});
      flowLog('CAMERA_FLOW', 'MIC_FRAME_SENT', {sessionId, trackId: cameraAudioTrack.id});
    }
    if (streamType === 'SCREEN' && screenDeviceAudioTrack) {
      pc?.addTrack(screenDeviceAudioTrack, stream);
      slog('PC', 'addTrack screen device audio', {kind: 'audio', id: screenDeviceAudioTrack.id});
      flowLog('AUDIO_FLOW', 'PLAYBACK_FRAME_SENT', {sessionId, trackId: screenDeviceAudioTrack.id});
    }

    const offerLabel = streamType === 'CAMERA' ? 'CAMERA_FLOW' : 'SCREEN_SHARE_FLOW';
    flowLog(offerLabel, 'OFFER_CREATING', {roomId, sessionId, streamType});
    const offer = await pc.createOffer({offerToReceiveAudio: false, offerToReceiveVideo: false});
    const offerHasVideo = sdpHasVideoMline(offer.sdp);
    flowLog(offerLabel, 'OFFER_CREATED', {roomId, sessionId, streamType, hasVideo: offerHasVideo});
    flowLog('WEBRTC_FLOW', offerHasVideo ? 'OFFER_CONTAINS_VIDEO' : 'OFFER_MISSING_VIDEO', {
      roomId,
      sessionId,
    });
    if (streamType === 'CAMERA' && !offerHasVideo) {
      slog('SDP', 'WARN camera offer has no video m-line — frames may not be flowing yet', {roomId, sessionId});
    }
    await pc.setLocalDescription(offer);
    streamStateTransition('LOCAL_DESCRIPTION_SET', 'connectChildStream', {roomId, sessionId});
    await signalingApi.post(roomId, 'offer', JSON.stringify(offer), sessionId);
    slog('SDP', 'offer sent', roomId);
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
    streamStateTransition('STREAMING', 'connectChildStream', {roomId, sessionId});
    markJsActive();
    void nativeModules.markJsStreamSessionActive(sessionId).catch(() => undefined);
    slog('PC', 'negotiation complete', {
      ice: pc?.iceConnectionState,
      connection: pc?.connectionState,
    });
  } catch (err) {
    streamStateTransition('FAILED', 'connectChildStream', {error: String(err)});
    slog('FLOW', 'connectChildStream FAILED', String(err));
    throw err;
  } finally {
    connecting = false;
    slog('FLOW', 'connectChildStream END', {roomId, ice: pc?.iceConnectionState});
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

export async function disconnectStream(clearConnecting = true): Promise<void> {
  if (activeStreamType === 'CAMERA') {
    flowLog('CAMERA_FLOW', 'STREAM_STOPPED', {room: activeRoomId, sessionId: activeSessionId});
  }
  slog('FLOW', 'disconnectStream', {room: activeRoomId, clearConnecting});
  detachIceRecovery?.();
  detachIceRecovery = null;
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
    // Track facing ourselves — CameraX/hidden tracks do not always expose facingMode.
    const nextFacing = currentCameraFacing === 'environment' ? 'user' : 'environment';
    slog('CAMERA', 'switching', {from: currentCameraFacing, to: nextFacing});

    // Prefer native absolute facing switch on the live HiddenCameraBridge capturer.
    // applyConstraints rebind-after-switch left Flip one-shot on Xiaomi.
    if (Platform.OS === 'android') {
      try {
        const result = await nativeModules.switchHiddenCameraFacing(nextFacing);
        currentCameraFacing =
          result?.facingMode === 'environment' ? 'environment' : nextFacing;
        slog('CAMERA', 'switched via native HiddenCameraBridge', {
          facing: currentCameraFacing,
        });
        return;
      } catch (err) {
        slog('CAMERA', 'native switchFacing failed — trying applyConstraints', String(err));
      }
    }

    const trackWithConstraints = videoTrack as MediaStreamTrack & {
      applyConstraints?: (c: {
        width?: number;
        height?: number;
        frameRate?: number;
        facingMode?: string;
      }) => Promise<void>;
      getSettings?: () => {
        width?: number;
        height?: number;
        frameRate?: number;
        facingMode?: string;
      };
    };
    if (typeof trackWithConstraints.applyConstraints === 'function') {
      const settings =
        typeof trackWithConstraints.getSettings === 'function'
          ? trackWithConstraints.getSettings()
          : {};
      try {
        await trackWithConstraints.applyConstraints({
          width: settings.width || 1280,
          height: settings.height || 720,
          frameRate: settings.frameRate || 30,
          facingMode: nextFacing,
        });
        currentCameraFacing = nextFacing;
        slog('CAMERA', 'switched via applyConstraints', {facing: nextFacing});
        return;
      } catch (err) {
        slog('CAMERA', 'applyConstraints failed — trying replaceTrack', String(err));
      }
    }

    // Fallback: replaceTrack via hidden camera path (backgrounded Android 14+).
    if (Platform.OS === 'android') {
      const newStream = await nativeModules.getHiddenCameraMediaStream(nextFacing);
      const newTrack = new MediaStream({
        streamId: newStream.streamId,
        streamReactTag: newStream.streamId,
        tracks: [newStream.track],
      } as {streamId: string; streamReactTag: string; tracks: object[]}).getVideoTracks()[0];
      if (!newTrack) {
        throw new Error('Couldn’t switch cameras. Please try again.');
      }
      const sender = pc.getSenders().find(s => s.track?.kind === 'video');
      if (sender) {
        await sender.replaceTrack(newTrack);
      } else {
        pc.addTrack(newTrack, activeMediaStream);
      }
      try {
        videoTrack.stop();
      } catch {
        // Old track may already be released by HiddenCameraBridge.stopActiveCapture.
      }
      activeMediaStream.removeTrack(videoTrack);
      activeMediaStream.addTrack(newTrack);
      currentCameraFacing = nextFacing;
      slog('CAMERA', 'switched via hidden capture replaceTrack', {facing: nextFacing});
      return;
    }

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
  } finally {
    clearTimeout(switchWatchdog);
    cameraSwitchInFlight = false;
  }
}
