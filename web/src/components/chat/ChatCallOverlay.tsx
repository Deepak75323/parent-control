import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import {chatApi, monitoringApi} from '../../api/services';
import {
  isPolitePeer,
  looksLikeUserId,
  normalizeCallUserId,
  remoteCallParticipantIds,
  resolveCallPeerLabel,
  sameCallUserId,
} from '../../lib/chatCallMesh';
import {subscribeChatCallSignals} from '../../lib/chatCallSignals';
import {resolveChatDisplayNickname} from '../../lib/chatDisplayNickname';
import {useChatCallSessionStore} from '../../realtime/chatCallSessionStore';
import {useRealtimeStore} from '../../realtime/realtimeStore';
import type {ChatCallItem, ChatMemberItem} from '../../types/api';

type Props = {
  roomId: string;
  call: ChatCallItem;
  myUserId: string;
  canEnd: boolean;
  /** Start without camera (voice-first). User can still turn camera on. */
  audioOnly?: boolean;
  /** Room / voice channel title. */
  roomName?: string;
  /** Channel subtitle shown in workspace header. */
  channelName?: string;
  /**
   * `workspace` = dedicated main-area video stage (default).
   * `dock` = legacy strip above messages.
   */
  layout?: 'workspace' | 'dock';
  /** Extra nicknames from room members (covers peers before call payload has them). */
  members?: ChatMemberItem[];
  onLeft: () => void;
  onEnded: () => void;
  onError: (message: string) => void;
  onOpenParticipants?: () => void;
  /** Controlled minimize — when true, show floating PiP so text chat can be used (Discord-like). */
  minimized?: boolean;
  onMinimizedChange?: (minimized: boolean) => void;
  /** When minimized off /chat, hide the connected bar — only floating PiP. */
  pipOnly?: boolean;
};

type PeerTile = {
  userId: string;
  label: string;
  stream: MediaStream | null;
  connectionState: RTCPeerConnectionState;
};

type PeerEntry = {
  pc: RTCPeerConnection;
  makingOffer: boolean;
  ignoreOffer: boolean;
  polite: boolean;
  pendingIce: RTCIceCandidateInit[];
  remoteStream: MediaStream;
  iceRestarted: boolean;
};

export function ChatCallOverlay({
  roomId,
  call,
  myUserId,
  canEnd,
  audioOnly = false,
  roomName,
  channelName,
  layout = 'workspace',
  members = [],
  onLeft,
  onEnded,
  onError,
  onOpenParticipants,
  minimized: minimizedProp = false,
  onMinimizedChange,
  pipOnly = false,
}: Props) {
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const pipLocalVideoRef = useRef<HTMLVideoElement>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const leftRef = useRef(false);
  const myId = normalizeCallUserId(myUserId);
  const peersRef = useRef<Map<string, PeerEntry>>(new Map());
  /** Always latest participants — mount effect must not close over a stale join list. */
  const callParticipantsRef = useRef(call.participants);
  callParticipantsRef.current = call.participants;
  const pendingPeerIdsRef = useRef<Set<string>>(new Set());
  const [peerTiles, setPeerTiles] = useState<PeerTile[]>([]);
  const micOn = useChatCallSessionStore(s => s.micOn);
  const camOn = useChatCallSessionStore(s => s.camOn);
  const deafened = useChatCallSessionStore(s => s.deafened);
  const leaveNonce = useChatCallSessionStore(s => s.leaveNonce);
  const setCamOn = useChatCallSessionStore(s => s.setCamOn);
  const storeToggleMic = useChatCallSessionStore(s => s.toggleMic);
  const storeToggleDeafen = useChatCallSessionStore(s => s.toggleDeafen);
  const storeToggleCam = useChatCallSessionStore(s => s.toggleCam);
  const deafenedRef = useRef(deafened);
  const [busy, setBusy] = useState(false);
  const minimized = minimizedProp;
  const setMinimized = (next: boolean) => onMinimizedChange?.(next);
  const pipRef = useRef<HTMLDivElement>(null);
  const [pipPos, setPipPos] = useState({right: 24, bottom: 24});
  const dragRef = useRef<{ox: number; oy: number; right: number; bottom: number} | null>(null);
  const chatCallEvent = useRealtimeStore(s => s.chatCallEvent);
  const chatCallEpoch = useRealtimeStore(s => s.chatCallEpoch);
  const callId = call.id;
  const participantsKey = call.participants.map(p => normalizeCallUserId(p.userId)).join(',');

  const tileCount = 1 + peerTiles.length;
  const gridClass =
    tileCount <= 1
      ? 'chat-call-stage chat-call-stage--solo'
      : tileCount === 2
        ? 'chat-call-stage chat-call-stage--duo'
        : 'chat-call-stage chat-call-stage--multi';

  const participantLabel = useMemo(() => {
    const map = new Map<string, string>();
    for (const member of members) {
      map.set(
        normalizeCallUserId(member.userId),
        resolveChatDisplayNickname(member.nickname, member.label),
      );
    }
    for (const p of call.participants) {
      map.set(
        normalizeCallUserId(p.userId),
        resolveChatDisplayNickname(p.nickname, p.label),
      );
    }
    return map;
  }, [call.participants, members]);

  function peerLabelFor(userId: string): string {
    return resolveCallPeerLabel(userId, participantLabel);
  }

  function upsertPeerTile(
    userId: string,
    label: string,
    stream: MediaStream | null,
    connectionState?: RTCPeerConnectionState,
  ) {
    const id = normalizeCallUserId(userId);
    if (!id || sameCallUserId(id, myId)) {
      return;
    }
    const safeLabel = looksLikeUserId(label) ? peerLabelFor(id) : label;
    setPeerTiles(prev => {
      const existing = prev.find(p => p.userId === id);
      if (existing) {
        return prev.map(p =>
          p.userId === id
            ? {
                ...p,
                label: safeLabel,
                stream: stream ?? p.stream,
                connectionState: connectionState ?? p.connectionState,
              }
            : p,
        );
      }
      return [
        ...prev,
        {
          userId: id,
          label: safeLabel,
          stream,
          connectionState: connectionState ?? 'new',
        },
      ];
    });
  }

  function removePeer(userId: string) {
    const id = normalizeCallUserId(userId);
    const peer = peersRef.current.get(id);
    peer?.pc.close();
    peersRef.current.delete(id);
    pendingPeerIdsRef.current.delete(id);
    setPeerTiles(prev => prev.filter(p => p.userId !== id));
  }

  async function loadIceServers(): Promise<RTCIceServer[]> {
    // Subject-scoped TURN (no streaming sessionId) — chat calls are not ScreenSessions.
    try {
      const {data} = await monitoringApi.webrtcConfig();
      const servers = (data.data?.iceServers ?? []).map(s => ({
        urls: s.urls,
        username: s.username,
        credential: s.credential,
      }));
      if (servers.length > 0) {
        return servers;
      }
    } catch {
      /* fall through to public STUN */
    }
    return [{urls: 'stun:stun.l.google.com:19302'}];
  }

  async function sendSignal(
    toUserId: string,
    messageType: 'OFFER' | 'ANSWER' | 'ICE',
    payload: unknown,
  ) {
    await chatApi.signalCall(roomId, callId, {
      toUserId,
      messageType,
      payload: JSON.stringify(payload),
    });
  }

  async function flushPendingIce(entry: PeerEntry) {
    if (!entry.pc.remoteDescription) {
      return;
    }
    const queued = entry.pendingIce.splice(0, entry.pendingIce.length);
    for (const candidate of queued) {
      try {
        await entry.pc.addIceCandidate(candidate);
      } catch {
        /* ignore stale candidates */
      }
    }
  }

  async function ensurePeer(remoteUserId: string, label: string) {
    const id = normalizeCallUserId(remoteUserId);
    if (!id || sameCallUserId(id, myId)) {
      return peersRef.current.get(id);
    }
    if (peersRef.current.has(id)) {
      const existing = peersRef.current.get(id)!;
      // Peer may have been created before getUserMedia finished — attach missing tracks.
      attachLocalTracks(existing.pc);
      upsertPeerTile(id, label, existing.remoteStream);
      return existing;
    }
    const iceServers = await loadIceServers();
    const pc = new RTCPeerConnection({iceServers});
    const polite = isPolitePeer(myId, id);
    const entry: PeerEntry = {
      pc,
      makingOffer: false,
      ignoreOffer: false,
      polite,
      pendingIce: [],
      remoteStream: new MediaStream(),
      iceRestarted: false,
    };
    peersRef.current.set(id, entry);
    pendingPeerIdsRef.current.delete(id);
    upsertPeerTile(id, label, null, pc.connectionState);

    attachLocalTracks(pc);

    pc.onicecandidate = event => {
      if (event.candidate) {
        void sendSignal(id, 'ICE', event.candidate.toJSON());
      }
    };
    pc.onconnectionstatechange = () => {
      upsertPeerTile(id, label, entry.remoteStream, pc.connectionState);
      if (pc.connectionState === 'failed' && !entry.iceRestarted) {
        entry.iceRestarted = true;
        try {
          pc.restartIce();
        } catch {
          /* older browsers */
        }
      }
    };
    pc.ontrack = event => {
      const {track} = event;
      if (!entry.remoteStream.getTracks().includes(track)) {
        entry.remoteStream.addTrack(track);
      }
      // Also pull any sibling tracks already on the inbound stream.
      for (const sibling of event.streams[0]?.getTracks() ?? []) {
        if (!entry.remoteStream.getTracks().includes(sibling)) {
          entry.remoteStream.addTrack(sibling);
        }
      }
      if (track.kind === 'audio') {
        track.enabled = !deafenedRef.current;
      }
      const refresh = () =>
        upsertPeerTile(id, label, entry.remoteStream, pc.connectionState);
      track.addEventListener('unmute', refresh);
      track.addEventListener('mute', refresh);
      track.addEventListener('ended', refresh);
      refresh();
    };
    pc.onnegotiationneeded = async () => {
      try {
        entry.makingOffer = true;
        await pc.setLocalDescription();
        if (pc.localDescription) {
          await sendSignal(id, 'OFFER', pc.localDescription);
        }
      } catch (e) {
        console.warn('negotiationneeded failed', e);
      } finally {
        entry.makingOffer = false;
      }
    };

    return entry;
  }

  function attachLocalTracks(pc: RTCPeerConnection) {
    const local = localStreamRef.current;
    if (!local) {
      return;
    }
    const senders = pc.getSenders();
    for (const track of local.getTracks()) {
      const already = senders.some(sender => sender.track?.id === track.id);
      if (!already) {
        pc.addTrack(track, local);
      }
    }
  }

  async function handleSignal(
    fromUserId: string,
    messageType: string,
    payloadRaw: string,
    label: string,
  ) {
    if (sameCallUserId(fromUserId, myId)) {
      return;
    }
    const entry = await ensurePeer(fromUserId, label);
    if (!entry) {
      return;
    }
    const {pc, polite} = entry;
    const payload = JSON.parse(payloadRaw) as RTCSessionDescriptionInit | RTCIceCandidateInit;

    if (messageType === 'ICE') {
      const candidate = payload as RTCIceCandidateInit;
      if (!pc.remoteDescription) {
        entry.pendingIce.push(candidate);
        return;
      }
      try {
        await pc.addIceCandidate(candidate);
      } catch {
        /* ignore late candidates */
      }
      return;
    }

    const description = payload as RTCSessionDescriptionInit;
    const offerCollision =
      description.type === 'offer' && (entry.makingOffer || pc.signalingState !== 'stable');
    entry.ignoreOffer = !polite && offerCollision;
    if (entry.ignoreOffer) {
      return;
    }
    await pc.setRemoteDescription(description);
    await flushPendingIce(entry);
    if (description.type === 'offer') {
      await pc.setLocalDescription();
      if (pc.localDescription) {
        await sendSignal(normalizeCallUserId(fromUserId), 'ANSWER', pc.localDescription);
      }
    }
  }

  function cleanupMediaOnly() {
    for (const {pc} of peersRef.current.values()) {
      pc.close();
    }
    peersRef.current.clear();
    setPeerTiles([]);
    localStreamRef.current?.getTracks().forEach(t => t.stop());
    localStreamRef.current = null;
  }

  async function leaveOnServer() {
    if (leftRef.current) {
      return;
    }
    leftRef.current = true;
    try {
      await chatApi.leaveCall(roomId, callId);
    } catch {
      /* best effort on unload */
    }
  }

  function attachLocalPreview(el: HTMLVideoElement | null) {
    if (el && localStreamRef.current) {
      el.srcObject = localStreamRef.current;
    }
  }

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) {
          onError(
            'Camera and microphone need a secure link (HTTPS). Open the portal over HTTPS and try again.',
          );
          return;
        }
        const stream = await navigator.mediaDevices.getUserMedia({audio: true, video: true});
        if (cancelled) {
          stream.getTracks().forEach(t => t.stop());
          return;
        }
        if (audioOnly) {
          stream.getVideoTracks().forEach(track => {
            track.enabled = false;
          });
          setCamOn(false);
        }
        localStreamRef.current = stream;
        // Apply current session mute/deafen/cam preferences.
        const session = useChatCallSessionStore.getState();
        stream.getAudioTracks().forEach(track => {
          track.enabled = session.micOn && !session.deafened;
        });
        stream.getVideoTracks().forEach(track => {
          track.enabled = session.camOn && !audioOnly;
        });
        attachLocalPreview(localVideoRef.current);
        attachLocalPreview(pipLocalVideoRef.current);
        // Attach (or re-attach) local tracks to every peer — covers CALL_JOINED races.
        for (const peer of peersRef.current.values()) {
          attachLocalTracks(peer.pc);
        }
        const pending = [
          ...remoteCallParticipantIds(callParticipantsRef.current, myId),
          ...pendingPeerIdsRef.current,
        ];
        pendingPeerIdsRef.current.clear();
        for (const userId of new Set(pending)) {
          await ensurePeer(userId, peerLabelFor(userId));
        }
      } catch (e) {
        onError(e instanceof Error ? e.message : 'Camera/microphone permission denied');
      }
    })();

    const onPageHide = () => {
      void leaveOnServer();
      cleanupMediaOnly();
    };
    window.addEventListener('pagehide', onPageHide);

    return () => {
      cancelled = true;
      window.removeEventListener('pagehide', onPageHide);
      cleanupMediaOnly();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount once per call
  }, [callId, roomId, myId]);

  useEffect(() => {
    attachLocalPreview(localVideoRef.current);
    attachLocalPreview(pipLocalVideoRef.current);
  }, [minimized, camOn]);

  // Keep peer tiles aligned with server active participants (drops ghosts / self).
  useEffect(() => {
    const allowed = new Set(remoteCallParticipantIds(call.participants, myId));
    for (const id of [...peersRef.current.keys()]) {
      if (!allowed.has(id)) {
        removePeer(id);
      }
    }
    for (const userId of allowed) {
      if (!peersRef.current.has(userId) && localStreamRef.current) {
        void ensurePeer(userId, peerLabelFor(userId));
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [participantsKey, myId]);

  // Refresh nicknames when call participants / room members arrive after signaling.
  useEffect(() => {
    setPeerTiles(prev =>
      prev.map(peer => {
        const next = peerLabelFor(peer.userId);
        return next === peer.label ? peer : {...peer, label: next};
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [participantLabel]);

  useEffect(() => {
    if (!chatCallEvent || chatCallEvent.roomId !== roomId || chatCallEvent.callId !== callId) {
      return;
    }
    if (chatCallEvent.eventType === 'CALL_ENDED') {
      leftRef.current = true;
      cleanupMediaOnly();
      onEnded();
      return;
    }
    if (chatCallEvent.eventType === 'CALL_LEFT' && chatCallEvent.userId) {
      if (sameCallUserId(chatCallEvent.userId, myId)) {
        return;
      }
      removePeer(chatCallEvent.userId);
      return;
    }
    if (chatCallEvent.eventType === 'CALL_JOINED' && chatCallEvent.userId) {
      if (sameCallUserId(chatCallEvent.userId, myId)) {
        return;
      }
      const joinedId = normalizeCallUserId(chatCallEvent.userId);
      if (!localStreamRef.current) {
        // Camera not ready yet — queue until getUserMedia finishes.
        pendingPeerIdsRef.current.add(joinedId);
        return;
      }
      void ensurePeer(joinedId, peerLabelFor(joinedId));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chatCallEpoch]);

  // Process every CALL_SIGNAL immediately (bypasses React state coalescing).
  useEffect(() => {
    return subscribeChatCallSignals(event => {
      if (event.roomId !== roomId || event.callId !== callId) {
        return;
      }
      if (
        !event.userId ||
        !sameCallUserId(event.toUserId, myId) ||
        !event.messageType ||
        !event.payload
      ) {
        return;
      }
      const fromId = normalizeCallUserId(event.userId);
      void handleSignal(fromId, event.messageType, event.payload, peerLabelFor(fromId));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [callId, roomId, myId, participantsKey]);

  async function leave() {
    setBusy(true);
    try {
      await leaveOnServer();
      cleanupMediaOnly();
      onLeft();
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Could not leave call');
    } finally {
      setBusy(false);
    }
  }

  async function endForEveryone() {
    setBusy(true);
    try {
      leftRef.current = true;
      await chatApi.endCall(roomId, callId);
      cleanupMediaOnly();
      onEnded();
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Could not end call');
    } finally {
      setBusy(false);
    }
  }

  function applyRemoteAudioEnabled(enabled: boolean) {
    for (const peer of peersRef.current.values()) {
      peer.remoteStream.getAudioTracks().forEach(track => {
        track.enabled = enabled;
      });
    }
  }

  // Keep media tracks in sync with Discord-style sidebar / toolbar controls.
  useEffect(() => {
    deafenedRef.current = deafened;
    applyRemoteAudioEnabled(!deafened);
    localStreamRef.current?.getAudioTracks().forEach(track => {
      track.enabled = micOn && !deafened;
    });
  }, [micOn, deafened]);

  useEffect(() => {
    localStreamRef.current?.getVideoTracks().forEach(track => {
      track.enabled = camOn;
    });
  }, [camOn]);

  useEffect(() => {
    if (leaveNonce > 0) {
      void leave();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [leaveNonce]);

  function toggleMic() {
    storeToggleMic();
  }

  function toggleCam() {
    storeToggleCam();
  }

  function toggleDeafen() {
    storeToggleDeafen();
  }

  const toolbar = (
    <div className="chat-call-float-bar" role="toolbar" aria-label="Call controls">
      <CallIconButton
        label={micOn ? 'Mute' : 'Unmute'}
        off={!micOn}
        disabled={busy}
        onClick={toggleMic}>
        {micOn ? <IconMic /> : <IconMicOff />}
      </CallIconButton>
      <CallIconButton
        label={camOn ? 'Turn camera off' : 'Turn camera on'}
        off={!camOn}
        disabled={busy}
        onClick={toggleCam}>
        {camOn ? <IconCam /> : <IconCamOff />}
      </CallIconButton>
      <CallIconButton
        label={deafened ? 'Undeafen' : 'Deafen'}
        off={deafened}
        disabled={busy}
        onClick={toggleDeafen}>
        {deafened ? <IconDeafenOff /> : <IconDeafen />}
      </CallIconButton>
      {onOpenParticipants ? (
        <CallIconButton
          label="Participants"
          disabled={busy}
          onClick={onOpenParticipants}>
          <IconParticipants />
        </CallIconButton>
      ) : null}
      <CallIconButton
        label="Minimize"
        disabled={busy}
        showLabel
        onClick={() => setMinimized(true)}>
        <IconMinimize />
      </CallIconButton>
      <CallIconButton
        label="Leave call"
        danger
        disabled={busy}
        onClick={() => void leave()}>
        <IconLeave />
      </CallIconButton>
      {canEnd ? (
        <CallIconButton
          label="End call for everyone"
          danger
          disabled={busy}
          onClick={() => void endForEveryone()}>
          <IconEnd />
        </CallIconButton>
      ) : null}
    </div>
  );

  const stage = (
    <div className={gridClass}>
      <div className="chat-call-tile chat-call-tile--local">
        {camOn ? (
          <video
            ref={el => {
              localVideoRef.current = el;
              attachLocalPreview(el);
            }}
            className="chat-call-video--mirror"
            autoPlay
            playsInline
            muted
          />
        ) : (
          <div className="chat-call-avatar" aria-hidden>
            You
          </div>
        )}
        <span className="chat-call-tile-label">
          You{!micOn ? ' · Muted' : ''}
          {deafened ? ' · Deafened' : ''}
        </span>
      </div>
      {peerTiles.map(peer => (
        <PeerVideo
          key={peer.userId}
          label={peer.label}
          stream={peer.stream}
          connectionState={peer.connectionState}
          audioMuted={deafened}
        />
      ))}
    </div>
  );

  // Collapsed: connected bar above chat + floating PiP (browse text/DMs while staying in call).
  if (minimized) {
    const primaryPeer = peerTiles[0] ?? null;
    return (
      <>
        {pipOnly ? null : (
        <div className="chat-call-connected-bar" role="status" aria-label="In call">
          <button
            type="button"
            className="chat-call-connected-main chat-call-connected-main--button"
            onClick={() => setMinimized(false)}
            title="Return to voice channel">
            <span className="chat-call-header-dot" aria-hidden />
            <div className="chat-call-connected-text">
              <strong>{audioOnly && !camOn ? 'Voice Connected' : 'Video Connected'}</strong>
              <span className="muted tiny">
                {channelName || roomName ? `${channelName || roomName} · ` : ''}
                {tileCount} in call — click to return
              </span>
            </div>
          </button>
          <div className="chat-call-connected-actions" role="toolbar" aria-label="Call controls">
            <CallIconButton
              label={micOn ? 'Mute' : 'Unmute'}
              off={!micOn}
              compact
              disabled={busy}
              onClick={toggleMic}>
              {micOn ? <IconMic /> : <IconMicOff />}
            </CallIconButton>
            <CallIconButton
              label={deafened ? 'Undeafen' : 'Deafen'}
              off={deafened}
              compact
              disabled={busy}
              onClick={toggleDeafen}>
              {deafened ? <IconDeafenOff /> : <IconDeafen />}
            </CallIconButton>
            <CallIconButton
              label={camOn ? 'Turn camera off' : 'Turn camera on'}
              off={!camOn}
              compact
              disabled={busy}
              onClick={toggleCam}>
              {camOn ? <IconCam /> : <IconCamOff />}
            </CallIconButton>
            <button
              type="button"
              className="chat-call-pip-expand"
              onClick={() => setMinimized(false)}
              aria-label="Return to voice channel">
              Expand
            </button>
            <CallIconButton
              label="Leave call"
              danger
              compact
              disabled={busy}
              onClick={() => void leave()}>
              <IconLeave />
            </CallIconButton>
          </div>
        </div>
        )}

        <div
          ref={pipRef}
          className="chat-call-pip"
          style={{right: pipPos.right, bottom: pipPos.bottom}}
          onPointerDown={event => {
            if ((event.target as HTMLElement).closest('button')) {
              return;
            }
            const el = pipRef.current;
            if (!el) {
              return;
            }
            dragRef.current = {
              ox: event.clientX,
              oy: event.clientY,
              right: pipPos.right,
              bottom: pipPos.bottom,
            };
            el.setPointerCapture(event.pointerId);
          }}
          onPointerMove={event => {
            const drag = dragRef.current;
            if (!drag) {
              return;
            }
            const dx = event.clientX - drag.ox;
            const dy = event.clientY - drag.oy;
            setPipPos({
              right: Math.max(8, drag.right - dx),
              bottom: Math.max(8, drag.bottom - dy),
            });
          }}
          onPointerUp={() => {
            dragRef.current = null;
          }}
          onPointerCancel={() => {
            dragRef.current = null;
          }}
          role="dialog"
          aria-label="Floating call preview">
          <div className="chat-call-pip-header">
            <span>
              {channelName || roomName || 'In call'} · {tileCount}
            </span>
            <button
              type="button"
              className="chat-call-pip-expand"
              onClick={() => setMinimized(false)}>
              Expand
            </button>
          </div>
          <div className="chat-call-pip-body">
            {primaryPeer ? (
              <PeerVideo
                label={primaryPeer.label}
                stream={primaryPeer.stream}
                connectionState={primaryPeer.connectionState}
                audioMuted={deafened}
                compact
              />
            ) : (
              <div className="chat-call-tile chat-call-tile--local chat-call-tile--compact">
                {camOn ? (
                  <video
                    ref={el => {
                      pipLocalVideoRef.current = el;
                      attachLocalPreview(el);
                    }}
                    autoPlay
                    muted
                    playsInline
                    className="chat-call-video chat-call-video--mirror"
                  />
                ) : (
                  <div className="chat-call-avatar" aria-hidden>
                    You
                  </div>
                )}
                <span className="chat-call-tile-label">You</span>
              </div>
            )}
          </div>
          <div className="chat-call-pip-toolbar" role="toolbar" aria-label="Floating call controls">
            <CallIconButton
              label={micOn ? 'Mute' : 'Unmute'}
              off={!micOn}
              compact
              disabled={busy}
              onClick={toggleMic}>
              {micOn ? <IconMic /> : <IconMicOff />}
            </CallIconButton>
            <CallIconButton
              label={camOn ? 'Camera off' : 'Camera on'}
              off={!camOn}
              compact
              disabled={busy}
              onClick={toggleCam}>
              {camOn ? <IconCam /> : <IconCamOff />}
            </CallIconButton>
            <CallIconButton
              label="Leave call"
              danger
              compact
              disabled={busy}
              onClick={() => void leave()}>
              <IconLeave />
            </CallIconButton>
          </div>
        </div>
      </>
    );
  }

  if (layout === 'dock') {
    return (
      <section className="chat-call-dock" aria-label="Video call">
        <header className="chat-call-dock-header">
          <div className="chat-call-header-status">
            <span className="chat-call-header-dot" aria-hidden />
            <span>{audioOnly && !camOn ? 'Voice Connected' : 'Video Connected'}</span>
            {channelName || roomName ? (
              <span className="chat-call-dock-room muted">{channelName || roomName}</span>
            ) : null}
          </div>
          <span className="chat-call-header-count">{tileCount} in call</span>
        </header>
        <div className="chat-call-dock-stage">{stage}</div>
        {toolbar}
      </section>
    );
  }

  // Dedicated main-content video workspace.
  return (
    <section className="chat-call-workspace" aria-label="Voice channel call">
      <header className="chat-call-workspace-header">
        <div className="chat-call-workspace-title">
          <span className="chat-call-workspace-icon" aria-hidden>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z" />
            </svg>
          </span>
          <div>
            <h2>{channelName || roomName || 'Voice channel'}</h2>
            {roomName && channelName ? (
              <p className="muted tiny">{roomName}</p>
            ) : (
              <p className="muted tiny">
                {audioOnly && !camOn ? 'Voice connected' : 'Video connected'}
              </p>
            )}
          </div>
        </div>
        <div className="chat-call-workspace-meta">
          <span className="chat-call-header-dot" aria-hidden />
          <span>{tileCount} in call</span>
          <button
            type="button"
            className="chat-call-minimize-header-btn"
            disabled={busy}
            onClick={() => setMinimized(true)}
            title="Shrink call so you can browse chat while staying connected">
            <IconMinimize />
            Minimize
          </button>
        </div>
      </header>
      <div className="chat-call-workspace-stage">{stage}</div>
      {toolbar}
    </section>
  );
}

function CallIconButton({
  label,
  children,
  onClick,
  off = false,
  danger = false,
  compact = false,
  disabled = false,
  showLabel = false,
}: {
  label: string;
  children: ReactNode;
  onClick: () => void;
  off?: boolean;
  danger?: boolean;
  compact?: boolean;
  disabled?: boolean;
  /** Show visible text beside the icon (e.g. Minimize). */
  showLabel?: boolean;
}) {
  const className = [
    'chat-call-icon-btn',
    compact ? 'chat-call-icon-btn--compact' : '',
    off ? 'chat-call-icon-btn--off' : '',
    danger ? 'chat-call-icon-btn--danger' : '',
    showLabel ? 'chat-call-icon-btn--labeled' : '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <button
      type="button"
      className={className}
      disabled={disabled}
      onClick={onClick}
      aria-label={label}
      title={label}>
      {children}
      {showLabel ? <span className="chat-call-icon-btn-label">{label}</span> : null}
    </button>
  );
}

function IconMic() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M12 14a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v5a3 3 0 0 0 3 3Zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2Z"
      />
    </svg>
  );
}

function IconMicOff() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M19 11h-1.7c0 .58-.12 1.13-.33 1.63l1.24 1.24A6.96 6.96 0 0 0 19 11ZM14.76 15.66l-1.45-1.45A2.98 2.98 0 0 1 12 14a3 3 0 0 1-3-3V8.91l-2-2V11c0 1.66 1.34 3 3 3 .54 0 1.04-.15 1.47-.4l1.29 1.29c-.84.55-1.82.91-2.86 1.03V21h2v-3.08c1.77-.22 3.35-1.08 4.5-2.36l1.4 1.4 1.41-1.41L4.41 2.86 3 4.27l4.1 4.1V11a5 5 0 0 0 5 5c.45 0 .88-.06 1.3-.17l1.36 1.36Z"
      />
    </svg>
  );
}

function IconDeafen() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M12 3a9 9 0 0 0-9 9v7a2 2 0 0 0 2 2h2v-8H5v-1a7 7 0 0 1 14 0v1h-2v8h2a2 2 0 0 0 2-2v-7a9 9 0 0 0-9-9Z"
      />
    </svg>
  );
}

function IconDeafenOff() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M2.1 3.51 3.51 2.1l18.38 18.39-1.41 1.41-3.1-3.1H17v-1.8l-2-2V19h2a2 2 0 0 0 2-2v-7c0-1.4-.35-2.72-.97-3.89L12.7 9.44A5 5 0 0 1 17 12v1h-2v-1c0-.34-.04-.67-.11-.99L2.1 3.51ZM7 13v8H5a2 2 0 0 1-2-2v-7a8.96 8.96 0 0 1 5.56-8.3l1.52 1.52A7 7 0 0 0 5 12v1h2Z"
      />
    </svg>
  );
}

function IconCam() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M17 10.5V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3.5l4 4v-11l-4 4Z"
      />
    </svg>
  );
}

function IconCamOff() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M3.27 2 2 3.27 4.73 6H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h11c.35 0 .68-.1.97-.26L19.73 22 21 20.73 3.27 2ZM17 10.5l4-4v11l-2.39-2.39.01-.11V10.5H17ZM15 8.82 7.18 16H4V8h9.18L15 8.82Z"
      />
    </svg>
  );
}

function IconMinimize() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M19 7h-8v6h8V7zm2-4H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14z"
      />
    </svg>
  );
}

function IconParticipants() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5C15 14.17 10.33 13 8 13zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"
      />
    </svg>
  );
}

function IconLeave() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2a1 1 0 0 1 1.01-.24 11.36 11.36 0 0 0 3.58.57 1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1 11.36 11.36 0 0 0 .57 3.58 1 1 0 0 1-.25 1.01l-2.2 2.2Z"
        transform="rotate(135 12 12)"
      />
    </svg>
  );
}

function IconEnd() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
      <path
        fill="currentColor"
        d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2Zm3.59 12.17-1.42 1.42L12 13.41l-2.17 2.18-1.42-1.42L10.59 12 8.41 9.83l1.42-1.42L12 10.59l2.17-2.18 1.42 1.42L13.41 12Z"
      />
    </svg>
  );
}

function PeerVideo({
  label,
  stream,
  compact = false,
  audioMuted = false,
  connectionState = 'new',
}: {
  label: string;
  stream: MediaStream | null;
  compact?: boolean;
  /** When true, remote audio is silenced (deafen). Video element stays muted for autoplay. */
  audioMuted?: boolean;
  connectionState?: RTCPeerConnectionState;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const audioRef = useRef<HTMLAudioElement>(null);
  // Track mute/unmute does not change React props — bump this so the tile re-renders.
  const [mediaEpoch, setMediaEpoch] = useState(0);
  const [hasFrame, setHasFrame] = useState(false);
  const [stalled, setStalled] = useState(false);
  const trackKey = stream
    ? stream
        .getTracks()
        .map(t => `${t.id}:${t.kind}:${t.readyState}:${t.enabled}`)
        .join('|')
    : '';
  const initials = label
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(w => w[0]?.toUpperCase() ?? '')
    .join('');

  useEffect(() => {
    setHasFrame(false);
    setStalled(false);
  }, [trackKey]);

  useEffect(() => {
    const video = videoRef.current;
    const audio = audioRef.current;
    if (!stream) {
      if (video) {
        video.srcObject = null;
      }
      if (audio) {
        audio.srcObject = null;
      }
      return;
    }

    // Video must stay muted so browsers allow autoplay; otherwise the tile stays black.
    if (video) {
      video.srcObject = stream;
      video.muted = true;
      void video.play().catch(() => {
        /* gesture may still be required in rare cases */
      });
    }

    // Play remote audio on a separate element so we can hear without blocking video.
    if (audio) {
      const audioOnly = new MediaStream(stream.getAudioTracks());
      audio.srcObject = audioOnly.getAudioTracks().length > 0 ? audioOnly : null;
      audio.muted = audioMuted;
      if (!audioMuted && audioOnly.getAudioTracks().length > 0) {
        void audio.play().catch(() => {
          /* may need a user gesture once */
        });
      }
    }

    const bump = () => setMediaEpoch(n => n + 1);
    const tryPlay = () => {
      void video?.play().catch(() => undefined);
      if (!audioMuted) {
        void audio?.play().catch(() => undefined);
      }
      bump();
    };
    const onEnded = () => bump();
    const checkFrame = () => {
      if (video && video.videoWidth > 0) {
        setHasFrame(true);
      }
    };
    stream.getTracks().forEach(track => {
      track.addEventListener('unmute', tryPlay);
      track.addEventListener('mute', bump);
      track.addEventListener('ended', onEnded);
    });
    video?.addEventListener('loadeddata', checkFrame);
    video?.addEventListener('resize', checkFrame);
    const framePoll = window.setInterval(checkFrame, 400);
    // Do not wait forever — surface a clear stall after ~20s.
    const stallTimer = window.setTimeout(() => setStalled(true), 20_000);
    return () => {
      window.clearInterval(framePoll);
      window.clearTimeout(stallTimer);
      video?.removeEventListener('loadeddata', checkFrame);
      video?.removeEventListener('resize', checkFrame);
      stream.getTracks().forEach(track => {
        track.removeEventListener('unmute', tryPlay);
        track.removeEventListener('mute', bump);
        track.removeEventListener('ended', onEnded);
      });
    };
  }, [stream, trackKey, audioMuted]);

  // mediaEpoch forces re-evaluation after mute/unmute/ended.
  void mediaEpoch;
  const videoTracks = stream?.getVideoTracks() ?? [];
  const hasEnabledVideo = videoTracks.some(t => t.readyState === 'live' && t.enabled);
  const cameraOff =
    Boolean(stream) &&
    (videoTracks.length === 0 || videoTracks.every(t => !t.enabled));
  const connectionFailed =
    connectionState === 'failed' || connectionState === 'disconnected';
  const waitingHint = !stream
    ? 'Connecting…'
    : cameraOff
      ? 'Camera off'
      : hasFrame
        ? null
        : connectionFailed
          ? 'Connection failed — leave and rejoin'
          : stalled
            ? 'Video not connecting — leave and rejoin'
            : 'Waiting for video…';
  const showPlaceholder = !hasFrame || Boolean(waitingHint);

  return (
    <div
      className={`chat-call-tile ${stream && hasEnabledVideo && hasFrame ? '' : 'chat-call-tile--waiting'}${
        compact ? ' chat-call-tile--compact' : ''
      }`}>
      {stream ? (
        <>
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className={hasFrame ? undefined : 'chat-call-video--hidden'}
          />
          <audio ref={audioRef} autoPlay />
        </>
      ) : null}
      {showPlaceholder ? (
        <div className="chat-call-avatar" aria-hidden>
          {initials || '?'}
        </div>
      ) : null}
      <span className="chat-call-tile-label">{label}</span>
      {waitingHint ? <span className="chat-call-tile-hint">{waitingHint}</span> : null}
    </div>
  );
}
