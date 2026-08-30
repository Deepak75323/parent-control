import {useCallback, useEffect, useRef, useState, type CSSProperties} from 'react';
import {monitoringApi, parentPinApi} from '../api/services';
import type {StreamType} from '../types/api';
import {shouldReleaseViewerOnStreamingEnded} from '../lib/streamTakeover';
import {
  nextViewerRotateDeg,
  prevViewerRotateDeg,
  viewerVideoRotateStyle,
  type ViewerRotateDeg,
} from '../lib/viewerVideoRotate';
import {
  toggleCameraFacing,
  webCameraObjectFit,
  type WebCameraFacing,
} from '../lib/cameraViewerLayout';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {
  connectBrowserViewer,
  disconnectBrowserViewer,
  getActiveRoomId,
  setRemoteAudioEnabled,
} from '../realtime/webrtcViewer';
import {CameraFrameViewer} from './CameraFrameViewer';

/**
   * Viewer-only rotate on web stays ON by default.
   * Set VITE_ENABLE_VIEWER_ROTATE=false to hide it. Parent mobile app is gated separately.
   */
const ENABLE_VIEWER_ROTATE =
  (import.meta.env.VITE_ENABLE_VIEWER_ROTATE as string | undefined)?.trim().toLowerCase() !==
  'false';

type Props = {
  deviceId: string;
};

function isElementFullscreen(el: HTMLElement | null): boolean {
  if (!el) {
    return false;
  }
  const doc = document as Document & {webkitFullscreenElement?: Element | null};
  return document.fullscreenElement === el || doc.webkitFullscreenElement === el;
}

async function requestElementFullscreen(el: HTMLElement): Promise<void> {
  const anyEl = el as HTMLElement & {
    webkitRequestFullscreen?: () => Promise<void> | void;
  };
  if (el.requestFullscreen) {
    await el.requestFullscreen();
    return;
  }
  if (anyEl.webkitRequestFullscreen) {
    await anyEl.webkitRequestFullscreen();
  }
}

async function exitDocumentFullscreen(): Promise<void> {
  const doc = document as Document & {
    webkitFullscreenElement?: Element | null;
    webkitExitFullscreen?: () => Promise<void> | void;
  };
  if (document.fullscreenElement && document.exitFullscreen) {
    await document.exitFullscreen();
    return;
  }
  if (doc.webkitFullscreenElement && doc.webkitExitFullscreen) {
    await doc.webkitExitFullscreen();
  }
}

export function LiveStreamPanel({deviceId}: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const videoWrapRef = useRef<HTMLDivElement>(null);
  const pendingStreamRef = useRef<MediaStream | null>(null);
  const roomIdRef = useRef<string | null>(null);
  const streamTypeRef = useRef<StreamType>('CAMERA');
  const [streamType, setStreamType] = useState<StreamType>('CAMERA');
  const [pin, setPin] = useState('');
  const [pinNeeded, setPinNeeded] = useState(false);
  const [status, setStatus] = useState('Idle');
  const [error, setError] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [muted, setMuted] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const [viewRotate, setViewRotate] = useState<ViewerRotateDeg>(0);
  const [wrapSize, setWrapSize] = useState({w: 0, h: 0});
  const [videoFrame, setVideoFrame] = useState({w: 0, h: 0});
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [cameraFacing, setCameraFacing] = useState<WebCameraFacing>('user');
  const [streamUprightRotate, setStreamUprightRotate] = useState<ViewerRotateDeg>(0);
  streamTypeRef.current = streamType;
  const streamingState = useRealtimeStore(s => s.streamingState);
  const isCameraLive = streamType === 'CAMERA' && Boolean(sessionId);
  const effectiveViewRotate: ViewerRotateDeg = ENABLE_VIEWER_ROTATE ? viewRotate : 0;
  const combinedViewerRotate = isCameraLive
    ? effectiveViewRotate
    : (((streamUprightRotate + effectiveViewRotate) % 360) as ViewerRotateDeg);

  const onCameraDimensions = useCallback((w: number, h: number) => {
    setVideoFrame({w, h});
  }, []);

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
    const syncFullscreen = () => {
      setFullscreen(isElementFullscreen(videoWrapRef.current));
    };
    document.addEventListener('fullscreenchange', syncFullscreen);
    document.addEventListener('webkitfullscreenchange', syncFullscreen);
    return () => {
      document.removeEventListener('fullscreenchange', syncFullscreen);
      document.removeEventListener('webkitfullscreenchange', syncFullscreen);
    };
  }, []);

  useEffect(() => {
    const wrap = videoWrapRef.current;
    if (!wrap || typeof ResizeObserver === 'undefined') {
      return;
    }
    const ro = new ResizeObserver(entries => {
      const rect = entries[0]?.contentRect;
      if (!rect) {
        return;
      }
      setWrapSize({w: Math.round(rect.width), h: Math.round(rect.height)});
    });
    ro.observe(wrap);
    return () => ro.disconnect();
  }, [sessionId, fullscreen, videoFrame.w, videoFrame.h]);

  useEffect(() => {
    const el = videoRef.current;
    if (!el || !sessionId || streamType === 'AUDIO' || streamType === 'CAMERA') {
      return;
    }
    const syncFrame = () => {
      if (el.videoWidth > 0 && el.videoHeight > 0) {
        setVideoFrame({w: el.videoWidth, h: el.videoHeight});
      }
    };
    el.addEventListener('loadedmetadata', syncFrame);
    el.addEventListener('resize', syncFrame);
    syncFrame();
    return () => {
      el.removeEventListener('loadedmetadata', syncFrame);
      el.removeEventListener('resize', syncFrame);
    };
  }, [sessionId, streamType, cameraFacing]);

  useEffect(() => {
    if (!streamingState) {
      return;
    }
    if (busy) {
      return;
    }
    if (streamingState.deviceId && streamingState.deviceId !== deviceId) {
      return;
    }
    const activeRoom = roomIdRef.current ?? getActiveRoomId();
    if (!shouldReleaseViewerOnStreamingEnded(streamingState, activeRoom, busy)) {
      return;
    }
    void disconnectBrowserViewer();
    roomIdRef.current = null;
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    pendingStreamRef.current = null;
    setRemoteStream(null);
    setSessionId(null);
    setBusy(false);
    setViewRotate(0);
    setStreamUprightRotate(0);
    setVideoFrame({w: 0, h: 0});
    setStatus('Moved to another client');
    setError('Live view continued on the parent app or another browser tab.');
  }, [streamingState, deviceId, busy]);

  const syncVideoDimensions = () => {
    const el = videoRef.current;
    if (!el || el.videoWidth <= 0 || el.videoHeight <= 0) {
      return;
    }
    setVideoFrame({w: el.videoWidth, h: el.videoHeight});
  };

  const attachStream = (stream: MediaStream) => {
    if (streamTypeRef.current === 'CAMERA') {
      setRemoteStream(stream);
      return;
    }
    const el = videoRef.current;
    if (!el) {
      pendingStreamRef.current = stream;
      return;
    }
    pendingStreamRef.current = null;
    el.srcObject = stream;
    void el.play()
      .then(syncVideoDimensions)
      .catch(() => undefined);
  };

  useEffect(() => {
    const el = videoRef.current;
    const pending = pendingStreamRef.current;
    if (!el || !pending || !sessionId || streamType === 'AUDIO' || streamType === 'CAMERA') {
      return;
    }
    pendingStreamRef.current = null;
    el.srcObject = pending;
    void el.play()
      .then(syncVideoDimensions)
      .catch(() => undefined);
  }, [sessionId, streamType]);

  useEffect(() => {
    const el = videoRef.current;
    if (!el || !sessionId || streamType === 'AUDIO' || streamType === 'CAMERA') {
      return;
    }
    const onMeta = () => syncVideoDimensions();
    el.addEventListener('loadedmetadata', onMeta);
    el.addEventListener('resize', onMeta);
    return () => {
      el.removeEventListener('loadedmetadata', onMeta);
      el.removeEventListener('resize', onMeta);
    };
  }, [sessionId, streamType]);

  const onStart = async () => {
    setBusy(true);
    setError(null);
    setStatus('Starting…');
    setViewRotate(0);
    setStreamUprightRotate(0);
    setVideoFrame({w: 0, h: 0});
    setRemoteStream(null);
    setCameraFacing('user');
    useRealtimeStore.getState().setStreamingState(undefined);
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
      pendingStreamRef.current = null;
      setRemoteStream(null);
      setSessionId(null);
      roomIdRef.current = null;
      setViewRotate(0);
      setStreamUprightRotate(0);
      setVideoFrame({w: 0, h: 0});
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
      setCameraFacing(toggleCameraFacing);
      setViewRotate(0);
      setVideoFrame({w: 0, h: 0});
    } catch {
      setError('Could not switch camera');
    }
  };

  const onToggleMute = () => {
    const next = !muted;
    setMuted(next);
    setRemoteAudioEnabled(!next);
  };

  const onToggleFullscreen = async () => {
    const wrap = videoWrapRef.current;
    if (!wrap) {
      return;
    }
    try {
      if (isElementFullscreen(wrap)) {
        await exitDocumentFullscreen();
      } else {
        await requestElementFullscreen(wrap);
      }
    } catch {
      setError('Fullscreen is not available in this browser');
    }
  };

  const videoWrapStyle: CSSProperties | undefined = undefined;

  const cameraObjectFit = isCameraLive ? webCameraObjectFit(cameraFacing) : 'contain';
  const videoStyle: CSSProperties | undefined = viewerVideoRotateStyle(
    combinedViewerRotate,
    wrapSize.w || 640,
    wrapSize.h || 400,
    isCameraLive ? cameraObjectFit : 'contain',
  );

  const videoClassName = ['live-video', isCameraLive ? 'live-video--camera' : '']
    .filter(Boolean)
    .join(' ');

  const cameraLandscape =
    isCameraLive && videoFrame.w > 0 && videoFrame.h > 0 && videoFrame.w > videoFrame.h;

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

      <div
        ref={videoWrapRef}
        className={[
          'video-wrap',
          isCameraLive ? 'video-wrap--camera' : '',
          cameraLandscape ? 'video-wrap--camera-landscape' : '',
          fullscreen ? 'video-wrap--fullscreen' : '',
        ]
          .filter(Boolean)
          .join(' ')}
        style={videoWrapStyle}>
        {sessionId && streamType !== 'AUDIO' ? (
          isCameraLive ? (
            <CameraFrameViewer
              stream={remoteStream}
              objectFit={cameraObjectFit}
              canvasClassName={videoClassName}
              viewerRotate={effectiveViewRotate}
              frontCamera={cameraFacing === 'user'}
              onVideoDimensions={onCameraDimensions}
            />
          ) : (
            <video
              ref={videoRef}
              className={videoClassName}
              style={videoStyle}
              autoPlay
              playsInline
              muted={streamType === 'SCREEN' ? false : muted}
              controls={false}
            />
          )
        ) : null}
        {streamType === 'AUDIO' && !sessionId ? (
          <p className="muted center-overlay">Audio-only — start to listen</p>
        ) : null}
        {sessionId && streamType !== 'AUDIO' ? (
          <div className="video-overlay-controls">
            {ENABLE_VIEWER_ROTATE ? (
              <>
                <button
                  type="button"
                  className="btn ghost"
                  onClick={() => setViewRotate(d => prevViewerRotateDeg(d))}
                  aria-label="Rotate view left">
                  ↺
                </button>
                <button
                  type="button"
                  className="btn ghost"
                  onClick={() => setViewRotate(d => nextViewerRotateDeg(d))}
                  aria-label="Rotate view right"
                  title={`View rotation ${viewRotate}°`}>
                  ↻ {viewRotate}°
                </button>
                {viewRotate !== 0 ? (
                  <button
                    type="button"
                    className="btn ghost"
                    onClick={() => setViewRotate(0)}
                    aria-label="Reset view rotation">
                    Reset
                  </button>
                ) : null}
              </>
            ) : null}
            <button
              type="button"
              className="btn ghost"
              onClick={() => void onToggleFullscreen()}
              aria-label={fullscreen ? 'Exit fullscreen' : 'Enter fullscreen'}>
              {fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
            </button>
          </div>
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
            {streamType !== 'AUDIO' ? (
              <>
                {ENABLE_VIEWER_ROTATE ? (
                  <button
                    type="button"
                    className="btn ghost"
                    onClick={() => setViewRotate(d => nextViewerRotateDeg(d))}
                    title="Rotate view only (does not move child camera)">
                    Rotate view {viewRotate}°
                  </button>
                ) : null}
                <button type="button" className="btn ghost" onClick={() => void onToggleFullscreen()}>
                  {fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
                </button>
              </>
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}
