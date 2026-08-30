import {useCallback, useEffect, useRef, useState, type CSSProperties} from 'react';
import {Link} from 'react-router-dom';
import type {StreamType} from '../types/api';
import {
  nextViewerRotateDeg,
  prevViewerRotateDeg,
  viewerVideoRotateStyle,
  type ViewerRotateDeg,
} from '../lib/viewerVideoRotate';
import {webCameraObjectFit} from '../lib/cameraViewerLayout';
import {CameraFrameViewer} from '../components/CameraFrameViewer';
import {useLiveStreamStore} from './liveStreamStore';

const ENABLE_VIEWER_ROTATE =
  (import.meta.env.VITE_ENABLE_VIEWER_ROTATE as string | undefined)?.trim().toLowerCase() !==
  'false';

type Props = {
  compact?: boolean;
};

export function StreamVideoView({compact = false}: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const videoWrapRef = useRef<HTMLDivElement>(null);
  const [wrapSize, setWrapSize] = useState({w: 0, h: 0});

  const sessionId = useLiveStreamStore(s => s.sessionId);
  const streamType = useLiveStreamStore(s => s.streamType);
  const remoteStream = useLiveStreamStore(s => s.remoteStream);
  const muted = useLiveStreamStore(s => s.muted);
  const viewRotate = useLiveStreamStore(s => s.viewRotate);
  const cameraFacing = useLiveStreamStore(s => s.cameraFacing);
  const videoFrame = useLiveStreamStore(s => s.videoFrame);
  const setVideoFrame = useLiveStreamStore(s => s.setVideoFrame);

  const isCameraLive = streamType === 'CAMERA' && Boolean(sessionId);
  const effectiveViewRotate: ViewerRotateDeg = ENABLE_VIEWER_ROTATE ? viewRotate : 0;
  const combinedViewerRotate = isCameraLive
    ? effectiveViewRotate
    : effectiveViewRotate;

  const onCameraDimensions = useCallback(
    (w: number, h: number) => setVideoFrame({w, h}),
    [setVideoFrame],
  );

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
  }, [sessionId, videoFrame.w, videoFrame.h]);

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
  }, [sessionId, streamType, cameraFacing, setVideoFrame]);

  useEffect(() => {
    const el = videoRef.current;
    const stream = remoteStream;
    if (!el || !stream || !sessionId || streamType === 'AUDIO' || streamType === 'CAMERA') {
      return;
    }
    el.srcObject = stream;
    void el.play().catch(() => undefined);
  }, [remoteStream, sessionId, streamType]);

  if (!sessionId || streamType === 'AUDIO') {
    return streamType === 'AUDIO' ? (
      <p className="muted center-overlay">Audio-only stream active</p>
    ) : null;
  }

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

  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const onChange = () => {
      setIsFullscreen(document.fullscreenElement === videoWrapRef.current);
    };
    document.addEventListener('fullscreenchange', onChange);
    return () => document.removeEventListener('fullscreenchange', onChange);
  }, [sessionId]);

  const toggleFullscreen = useCallback(() => {
    const el = videoWrapRef.current;
    if (!el) {
      return;
    }
    if (document.fullscreenElement === el) {
      void document.exitFullscreen();
      return;
    }
    void el.requestFullscreen?.().catch(() => undefined);
  }, []);

  useEffect(() => {
    if (compact || !sessionId) {
      useLiveStreamStore.getState().registerVideoFullscreenToggle(null);
      return;
    }
    useLiveStreamStore.getState().registerVideoFullscreenToggle(toggleFullscreen);
    return () => useLiveStreamStore.getState().registerVideoFullscreenToggle(null);
  }, [compact, sessionId, toggleFullscreen]);

  return (
    <div
      ref={videoWrapRef}
      className={[
        'video-wrap',
        compact ? 'video-wrap--pip' : '',
        isCameraLive ? 'video-wrap--camera' : '',
        cameraLandscape ? 'video-wrap--camera-landscape' : '',
        isFullscreen ? 'video-wrap--fullscreen' : '',
      ]
        .filter(Boolean)
        .join(' ')}>
      {isCameraLive ? (
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
      )}
      {!compact && sessionId ? (
        <div className="video-overlay-controls">
          <button type="button" className="btn ghost sm" onClick={toggleFullscreen}>
            {isFullscreen ? 'Exit full' : 'Full screen'}
          </button>
        </div>
      ) : null}
    </div>
  );
}

export function StreamControls({
  inline = false,
  compact = false,
  layoutInToolbar = false,
}: {
  inline?: boolean;
  compact?: boolean;
  /** When true, Large/Reset view/Reset size render in the PiP toolbar instead. */
  layoutInToolbar?: boolean;
}) {
  const sessionId = useLiveStreamStore(s => s.sessionId);
  const streamType = useLiveStreamStore(s => s.streamType);
  const busy = useLiveStreamStore(s => s.busy);
  const muted = useLiveStreamStore(s => s.muted);
  const viewRotate = useLiveStreamStore(s => s.viewRotate);
  const setViewRotate = useLiveStreamStore(s => s.setViewRotate);
  const resetView = useLiveStreamStore(s => s.resetView);
  const expandPip = useLiveStreamStore(s => s.expandPip);
  const resetPipLayout = useLiveStreamStore(s => s.resetPipLayout);
  const toggleVideoFullscreen = useLiveStreamStore(s => s.toggleVideoFullscreen);
  const stop = useLiveStreamStore(s => s.stop);
  const flipCamera = useLiveStreamStore(s => s.flipCamera);
  const toggleMute = useLiveStreamStore(s => s.toggleMute);

  const showLayout = compact && !layoutInToolbar;
  const showRotate = streamType !== 'AUDIO' && ENABLE_VIEWER_ROTATE;

  if (!sessionId) {
    return null;
  }

  return (
    <div className={`btn-row wrap${inline ? '' : ' stream-pip-controls'}`}>
      <button type="button" className="btn danger sm" disabled={busy} onClick={() => void stop()}>
        Stop
      </button>
      {streamType === 'CAMERA' ? (
        <button type="button" className="btn ghost sm" disabled={busy} onClick={() => void flipCamera()}>
          Flip
        </button>
      ) : null}
      <button type="button" className="btn ghost sm" onClick={toggleMute}>
        {muted ? 'Unmute' : 'Mute'}
      </button>
      {!compact ? (
        <button type="button" className="btn ghost sm" onClick={toggleVideoFullscreen}>
          Full screen
        </button>
      ) : null}
      {showLayout ? (
        <>
          <button type="button" className="btn ghost sm" onClick={expandPip}>
            Large
          </button>
          <button type="button" className="btn ghost sm" onClick={resetView}>
            Reset view
          </button>
          <button type="button" className="btn ghost sm" onClick={resetPipLayout}>
            Reset size
          </button>
        </>
      ) : null}
      {showRotate ? (
        <>
          <button type="button" className="btn ghost sm" onClick={() => setViewRotate(d => prevViewerRotateDeg(d))}>
            ↺
          </button>
          <button type="button" className="btn ghost sm" onClick={() => setViewRotate(d => nextViewerRotateDeg(d))}>
            ↻ {viewRotate}°
          </button>
        </>
      ) : null}
      {!compact && streamType !== 'AUDIO' ? (
        <button type="button" className="btn ghost sm" onClick={resetView}>
          Reset view
        </button>
      ) : null}
    </div>
  );
}

export function StreamPipToolbar() {
  const resetView = useLiveStreamStore(s => s.resetView);
  const expandPip = useLiveStreamStore(s => s.expandPip);
  const resetPipLayout = useLiveStreamStore(s => s.resetPipLayout);

  return (
    <div className="stream-pip-toolbar">
      <button type="button" className="btn ghost sm" onClick={expandPip}>
        Large
      </button>
      <button type="button" className="btn ghost sm" onClick={resetView}>
        Reset view
      </button>
      <button type="button" className="btn ghost sm" onClick={resetPipLayout}>
        Reset size
      </button>
    </div>
  );
}

export function StreamTypePicker({disabled}: {disabled?: boolean}) {
  const streamType = useLiveStreamStore(s => s.streamType);
  const setStreamType = useLiveStreamStore(s => s.setStreamType);
  return (
    <div className="btn-row wrap">
      {(['SCREEN', 'CAMERA', 'AUDIO'] as StreamType[]).map(t => (
        <button
          key={t}
          type="button"
          className={`btn ${streamType === t ? 'primary' : 'ghost'}`}
          disabled={disabled}
          onClick={() => setStreamType(t)}>
          {t === 'SCREEN' ? 'Screen' : t === 'CAMERA' ? 'Camera' : 'Audio'}
        </button>
      ))}
    </div>
  );
}

export function PipDeviceLink() {
  const deviceId = useLiveStreamStore(s => s.deviceId);
  const deviceName = useLiveStreamStore(s => s.deviceName);
  if (!deviceId) {
    return null;
  }
  return (
    <Link to={`/devices/${deviceId}`} className="stream-pip-open">
      {deviceName ?? 'Open device'}
    </Link>
  );
}
