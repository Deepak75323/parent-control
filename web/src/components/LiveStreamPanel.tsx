import {useEffect, useRef, useState} from 'react';
import {monitoringApi, parentPinApi} from '../api/services';
import type {StreamType} from '../types/api';
import {shouldReleaseViewerOnStreamingEnded} from '../lib/streamTakeover';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {
  connectBrowserViewer,
  disconnectBrowserViewer,
  getActiveRoomId,
  setRemoteAudioEnabled,
} from '../realtime/webrtcViewer';

type Props = {
  deviceId: string;
};

export function LiveStreamPanel({deviceId}: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const roomIdRef = useRef<string | null>(null);
  const [streamType, setStreamType] = useState<StreamType>('SCREEN');
  const [pin, setPin] = useState('');
  const [pinNeeded, setPinNeeded] = useState(false);
  const [status, setStatus] = useState('Idle');
  const [error, setError] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [muted, setMuted] = useState(false);
  const streamingState = useRealtimeStore(s => s.streamingState);

  useEffect(() => {
    parentPinApi
      .status()
      .then(res => setPinNeeded(Boolean(res.data.data?.pinConfigured)))
      .catch(() => undefined);
    return () => {
      void disconnectBrowserViewer();
      roomIdRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!streamingState) {
      return;
    }
    if (streamingState.deviceId && streamingState.deviceId !== deviceId) {
      return;
    }
    const activeRoom = roomIdRef.current ?? getActiveRoomId();
    if (!shouldReleaseViewerOnStreamingEnded(streamingState, activeRoom)) {
      return;
    }
    void disconnectBrowserViewer();
    roomIdRef.current = null;
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setSessionId(null);
    setBusy(false);
    setStatus('Moved to another client');
    setError('Live view continued on the parent app or another browser tab.');
  }, [streamingState, deviceId]);

  const attachStream = (stream: MediaStream) => {
    const el = videoRef.current;
    if (!el) {
      return;
    }
    el.srcObject = stream;
    void el.play().catch(() => undefined);
  };

  const onStart = async () => {
    setBusy(true);
    setError(null);
    setStatus('Starting…');
    try {
      await disconnectBrowserViewer();
      const {data} = await monitoringApi.startStream(
        deviceId,
        streamType,
        pinNeeded && streamType === 'SCREEN' && pin ? pin : undefined,
      );
      const session = data.data;
      if (!session?.signalingRoomId || !session.sessionId) {
        throw new Error(data.message ?? 'No stream session returned');
      }
      roomIdRef.current = session.signalingRoomId;
      setSessionId(session.sessionId);
      setStatus(`Connecting (${session.status})…`);
      await connectBrowserViewer(session.signalingRoomId, session.sessionId, {
        onRemoteStream: attachStream,
        onConnectionState: s => setStatus(`ICE: ${s}`),
        onError: msg => setError(msg),
      });
      setStatus('Live');
    } catch (err: unknown) {
      let msg = 'Stream failed';
      if (err instanceof Error) {
        msg = err.message;
      }
      if (err && typeof err === 'object' && 'response' in err) {
        const response = (err as {response?: {data?: {message?: string}}}).response;
        if (response?.data?.message) {
          msg = response.data.message;
        }
      }
      setError(msg);
      setStatus('Failed');
      setSessionId(null);
      roomIdRef.current = null;
    } finally {
      setBusy(false);
    }
  };

  const onStop = async () => {
    setBusy(true);
    try {
      if (sessionId) {
        await monitoringApi.stopScreenShare(sessionId).catch(() => undefined);
      }
      await disconnectBrowserViewer();
      if (videoRef.current) {
        videoRef.current.srcObject = null;
      }
      setSessionId(null);
      roomIdRef.current = null;
      setStatus('Stopped');
    } finally {
      setBusy(false);
    }
  };

  const onFlip = async () => {
    if (!sessionId) {
      return;
    }
    try {
      await monitoringApi.switchCamera(sessionId);
    } catch {
      setError('Could not switch camera');
    }
  };

  const onToggleMute = () => {
    const next = !muted;
    setMuted(next);
    setRemoteAudioEnabled(!next);
  };

  return (
    <div className="card live-panel">
      <div className="page-header">
        <div>
          <h2>Live view</h2>
          <p className="muted">{status}</p>
        </div>
      </div>
      {error ? <div className="error">{error}</div> : null}

      <div className="btn-row wrap">
        {(['SCREEN', 'CAMERA', 'AUDIO'] as StreamType[]).map(t => (
          <button
            key={t}
            type="button"
            className={`btn ${streamType === t ? 'primary' : 'ghost'}`}
            disabled={busy || Boolean(sessionId)}
            onClick={() => setStreamType(t)}>
            {t === 'SCREEN' ? 'Screen' : t === 'CAMERA' ? 'Camera' : 'Audio'}
          </button>
        ))}
      </div>

      {pinNeeded && streamType === 'SCREEN' ? (
        <label className="form-row">
          Parent PIN (silent screen)
          <input
            type="password"
            inputMode="numeric"
            value={pin}
            onChange={e => setPin(e.target.value)}
            maxLength={8}
            disabled={busy || Boolean(sessionId)}
          />
        </label>
      ) : null}

      <div className="video-wrap">
        <video
          ref={videoRef}
          className="live-video"
          autoPlay
          playsInline
          muted={streamType === 'SCREEN' ? false : muted}
          controls={false}
        />
        {streamType === 'AUDIO' && !sessionId ? (
          <p className="muted center-overlay">Audio-only — start to listen</p>
        ) : null}
      </div>

      <div className="btn-row wrap">
        {!sessionId ? (
          <button type="button" className="btn primary" disabled={busy} onClick={() => void onStart()}>
            {busy ? 'Starting…' : 'Start'}
          </button>
        ) : (
          <>
            <button type="button" className="btn danger" disabled={busy} onClick={() => void onStop()}>
              Stop
            </button>
            {streamType === 'CAMERA' ? (
              <button type="button" className="btn ghost" disabled={busy} onClick={() => void onFlip()}>
                Flip camera
              </button>
            ) : null}
            <button type="button" className="btn ghost" onClick={onToggleMute}>
              {muted ? 'Unmute' : 'Mute'}
            </button>
          </>
        )}
      </div>
    </div>
  );
}
