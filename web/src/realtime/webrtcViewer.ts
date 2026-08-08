import {monitoringApi, signalingApi} from '../api/services';
import type {StreamType} from '../types/api';

export type ViewerCallbacks = {
  onRemoteStream: (stream: MediaStream) => void;
  onConnectionState: (state: string) => void;
  onError: (message: string) => void;
};

type SignalingPayload = {
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
const seenIds = new Set<string>();
const pendingIce: RTCIceCandidateInit[] = [];
let callbacks: ViewerCallbacks | null = null;

function parseSdp(payload: string): RTCSessionDescriptionInit {
  try {
    return JSON.parse(payload) as RTCSessionDescriptionInit;
  } catch {
    return {type: 'offer', sdp: payload};
  }
}

async function loadIceServers(sessionId?: string): Promise<RTCIceServer[]> {
  const servers: RTCIceServer[] = [];
  try {
    const {data} = await monitoringApi.webrtcConfig(sessionId);
    for (const s of data.data?.iceServers ?? []) {
      if (s.username && s.credential) {
        servers.push({urls: s.urls, username: s.username, credential: s.credential});
      } else if (s.urls?.length) {
        servers.push({urls: s.urls});
      }
    }
  } catch {
    /* fall through to STUN */
  }
  if (servers.length === 0) {
    servers.push({urls: 'stun:stun.l.google.com:19302'});
  }
  return servers;
}

async function flushPendingIce(): Promise<void> {
  if (!pc?.remoteDescription) {
    return;
  }
  while (pendingIce.length > 0) {
    const candidate = pendingIce.shift();
    if (!candidate || !pc) {
      continue;
    }
    try {
      await pc.addIceCandidate(candidate);
    } catch {
      pendingIce.unshift(candidate);
      break;
    }
  }
}

async function applyOffer(payload: string, messageId: string): Promise<boolean> {
  if (!pc || seenIds.has(messageId)) {
    return false;
  }
  if (pc.remoteDescription?.type === 'offer' && pc.localDescription?.type === 'answer') {
    // Child re-offer: rebuild peer
    const roomId = activeRoomId;
    const sessionId = activeSessionId;
    seenIds.clear();
    pendingIce.length = 0;
    remoteStream = null;
    pc.close();
    pc = null;
    if (!roomId) {
      return false;
    }
    await createPc(roomId, sessionId);
  }
  try {
    await pc!.setRemoteDescription(parseSdp(payload));
    seenIds.add(messageId);
    await flushPendingIce();
    const answer = await pc!.createAnswer();
    await pc!.setLocalDescription(answer);
    if (activeRoomId) {
      await signalingApi.post(activeRoomId, 'answer', JSON.stringify(answer), activeSessionId);
    }
    return true;
  } catch (err) {
    callbacks?.onError(`SDP offer failed: ${String(err)}`);
    return false;
  }
}

async function applyIce(payload: string, messageId: string): Promise<void> {
  if (!pc || seenIds.has(messageId)) {
    return;
  }
  const candidate = JSON.parse(payload) as RTCIceCandidateInit;
  if (!pc.remoteDescription) {
    pendingIce.push(candidate);
    seenIds.add(messageId);
    return;
  }
  try {
    await pc.addIceCandidate(candidate);
    seenIds.add(messageId);
  } catch {
    /* ignore bad candidates */
  }
}

async function handleMessage(msg: SignalingPayload): Promise<void> {
  if (!pc || msg.senderRole === 'PARENT') {
    return;
  }
  const messageId = msg.id ?? `${msg.messageType}-${msg.payload.slice(0, 32)}`;
  if (msg.messageType === 'offer') {
    await applyOffer(msg.payload, messageId);
    return;
  }
  if (msg.messageType === 'ice') {
    await applyIce(msg.payload, messageId);
  }
}

async function pollSignaling(): Promise<boolean> {
  if (!activeRoomId) {
    return false;
  }
  try {
    const {data} = await signalingApi.recent(activeRoomId);
    const messages = data.data ?? [];
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
      await handleMessage(payload);
    }
    if (latestOffer) {
      const messageId =
        latestOffer.id ?? `${latestOffer.messageType}-${latestOffer.payload.slice(0, 32)}`;
      await applyOffer(latestOffer.payload, messageId);
    }
    return pc?.remoteDescription?.type === 'offer';
  } catch {
    return false;
  }
}

async function waitForOffer(timeoutMs = 60000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline && pc && !pc.remoteDescription) {
    if (await pollSignaling()) {
      return true;
    }
    await new Promise(r => setTimeout(r, 400));
  }
  return pc?.remoteDescription?.type === 'offer';
}

async function waitForIce(timeoutMs = 60000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline && pc) {
    const state = pc.iceConnectionState;
    if (state === 'connected' || state === 'completed') {
      return true;
    }
    if (state === 'failed') {
      return false;
    }
    await new Promise(r => setTimeout(r, 250));
  }
  const state = pc?.iceConnectionState;
  return state === 'connected' || state === 'completed';
}

async function createPc(roomId: string, sessionId?: string): Promise<void> {
  const iceServers = await loadIceServers(sessionId);
  pc = new RTCPeerConnection({
    iceServers,
    iceTransportPolicy: 'all',
    bundlePolicy: 'max-bundle',
  });

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
      callbacks?.onRemoteStream(remoteStream);
    }
  };

  pc.oniceconnectionstatechange = () => {
    const state = pc?.iceConnectionState ?? 'unknown';
    callbacks?.onConnectionState(state);
    if (activeSessionId) {
      const streamType: StreamType = roomId.startsWith('camera_')
        ? 'CAMERA'
        : roomId.startsWith('audio_')
          ? 'AUDIO'
          : 'SCREEN';
      monitoringApi
        .reportIceConnectivity({
          streamType,
          sessionId: activeSessionId,
          iceConnectionState: state,
        })
        .catch(() => undefined);
    }
  };

  pc.onicecandidate = ev => {
    if (!ev.candidate) {
      return;
    }
    signalingApi
      .post(roomId, 'ice', JSON.stringify(ev.candidate.toJSON()), sessionId)
      .catch(() => undefined);
  };
}

export async function connectBrowserViewer(
  roomId: string,
  sessionId: string | undefined,
  cbs: ViewerCallbacks,
): Promise<void> {
  await disconnectBrowserViewer();
  callbacks = cbs;
  seenIds.clear();
  pendingIce.length = 0;
  remoteStream = null;
  activeRoomId = roomId;
  activeSessionId = sessionId;

  await createPc(roomId, sessionId);

  pollTimer = setInterval(() => {
    void pollSignaling();
  }, 500);

  const offerOk = await waitForOffer();
  if (!offerOk) {
    throw new Error(
      'Child stream did not start. Confirm the device is online and permissions are finished.',
    );
  }

  if (pollTimer) {
    clearInterval(pollTimer);
  }
  pollTimer = setInterval(() => {
    void pollSignaling();
  }, 2000);

  const iceOk = await waitForIce();
  if (!iceOk) {
    throw new Error(
      'WebRTC connection failed. Check network on both devices and TURN/coturn setup.',
    );
  }
}

export async function disconnectBrowserViewer(): Promise<void> {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  if (pc) {
    pc.close();
    pc = null;
  }
  remoteStream = null;
  activeRoomId = null;
  activeSessionId = undefined;
  seenIds.clear();
  pendingIce.length = 0;
  callbacks = null;
}

export function setRemoteAudioEnabled(enabled: boolean): void {
  remoteStream?.getAudioTracks().forEach(t => {
    t.enabled = enabled;
  });
}

/** Handle a STOMP push for the active room (optional live path). */
export function handleSignalingPush(payload: Record<string, unknown>): void {
  void handleMessage({
    id: payload.id as string | undefined,
    messageType: String(payload.messageType ?? ''),
    payload: String(payload.payload ?? ''),
    senderRole: String(payload.senderRole ?? ''),
  });
}

export function getActiveRoomId(): string | null {
  return activeRoomId;
}
