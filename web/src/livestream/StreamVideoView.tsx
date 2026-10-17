import {useCallback, useEffect, useRef, useState, type CSSProperties} from 'react';
import {Link} from 'react-router-dom';
import type {StreamType} from '../types/api';
import {
  clampViewerPan,
  formatViewerZoomLabel,
  nextPinchScale,
  nextViewerRotateDeg,
  nudgeViewerZoomPercent,
  prevViewerRotateDeg,
  viewerVideoRotateStyle,
  VIEWER_ZOOM_STEP_PERCENT,
  type ViewerRotateDeg,
} from '../lib/viewerVideoRotate';
import {webCameraObjectFit} from '../lib/cameraViewerLayout';
import {CameraFrameViewer} from '../components/CameraFrameViewer';
import {useLiveStreamStore} from './liveStreamStore';
import {openMeetStylePip} from './meetPipWindow';

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
  const viewZoomScale = useLiveStreamStore(s => s.viewZoomScale);
  const viewPanX = useLiveStreamStore(s => s.viewPanX);
  const viewPanY = useLiveStreamStore(s => s.viewPanY);
  const setViewZoomScale = useLiveStreamStore(s => s.setViewZoomScale);
  const setViewPan = useLiveStreamStore(s => s.setViewPan);
  const cameraFacing = useLiveStreamStore(s => s.cameraFacing);
  const videoFrame = useLiveStreamStore(s => s.videoFrame);
  const setVideoFrame = useLiveStreamStore(s => s.setVideoFrame);
  const setCameraFacing = useLiveStreamStore(s => s.setCameraFacing);

  const isCameraLive = streamType === 'CAMERA' && Boolean(sessionId);
  const effectiveViewRotate: ViewerRotateDeg = ENABLE_VIEWER_ROTATE ? viewRotate : 0;
  const combinedViewerRotate = isCameraLive
    ? effectiveViewRotate
    : effectiveViewRotate;

  const onCameraDimensions = useCallback(
    (w: number, h: number) => setVideoFrame({w, h}),
    [setVideoFrame],
  );

  const onFacingInferred = useCallback(
    (facing: 'user' | 'environment') => {
      if (useLiveStreamStore.getState().cameraFacing !== facing) {
        setCameraFacing(facing);
      }
    },
    [setCameraFacing],
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

  useEffect(() => {
    const el = videoWrapRef.current;
    if (!el || !sessionId || streamType === 'AUDIO') {
      return;
    }

    const pinchStartScaleRef = {current: 1};
    const pinchStartDistRef = {current: 0};

    const touchDistance = (touches: TouchList) => {
      if (touches.length < 2) {
        return 0;
      }
      return Math.hypot(
        touches[0].clientX - touches[1].clientX,
        touches[0].clientY - touches[1].clientY,
      );
    };

    const onTouchStart = (event: TouchEvent) => {
      if (event.touches.length === 2) {
        pinchStartScaleRef.current = useLiveStreamStore.getState().viewZoomScale;
        pinchStartDistRef.current = touchDistance(event.touches);
      }
    };

    const onTouchMove = (event: TouchEvent) => {
      if (event.touches.length !== 2 || pinchStartDistRef.current <= 0) {
        return;
      }
      event.preventDefault();
      const dist = touchDistance(event.touches);
      setViewZoomScale(
        nextPinchScale(pinchStartScaleRef.current, pinchStartDistRef.current, dist),
      );
    };

    const onTouchEnd = () => {
      pinchStartDistRef.current = 0;
    };

    const onWheel = (event: WheelEvent) => {
      if (!event.ctrlKey) {
        return;
      }
      event.preventDefault();
      const delta =
        event.deltaY < 0 ? VIEWER_ZOOM_STEP_PERCENT : -VIEWER_ZOOM_STEP_PERCENT;
      const current = useLiveStreamStore.getState().viewZoomScale;
      setViewZoomScale(nudgeViewerZoomPercent(current, delta));
    };

    el.addEventListener('touchstart', onTouchStart, {passive: true});
    el.addEventListener('touchmove', onTouchMove, {passive: false});
    el.addEventListener('touchend', onTouchEnd);
    el.addEventListener('touchcancel', onTouchEnd);
    el.addEventListener('wheel', onWheel, {passive: false});
    return () => {
      el.removeEventListener('touchstart', onTouchStart);
      el.removeEventListener('touchmove', onTouchMove);
      el.removeEventListener('touchend', onTouchEnd);
      el.removeEventListener('touchcancel', onTouchEnd);
      el.removeEventListener('wheel', onWheel);
    };
  }, [sessionId, streamType, setViewZoomScale]);

  useEffect(() => {
    const el = videoWrapRef.current;
    if (!el || !sessionId || streamType === 'AUDIO') {
      return;
    }
    const pointers = new Set<number>();
    let dragging = false;
    let pointerId = -1;
    let lastX = 0;
    let lastY = 0;

    const onDown = (event: PointerEvent) => {
      pointers.add(event.pointerId);
      if (event.button !== 0 || pointers.size > 1) {
        dragging = false;
        return;
      }
      const target = event.target;
      if (target instanceof Element && target.closest('button, a, input')) {
        return;
      }
      if (useLiveStreamStore.getState().viewZoomScale <= 1) {
        return;
      }
      dragging = true;
      pointerId = event.pointerId;
      lastX = event.clientX;
      lastY = event.clientY;
      el.setPointerCapture(event.pointerId);
    };

    const onMove = (event: PointerEvent) => {
      if (!dragging || event.pointerId !== pointerId || pointers.size > 1) {
        return;
      }
      const dx = event.clientX - lastX;
      const dy = event.clientY - lastY;
      lastX = event.clientX;
      lastY = event.clientY;
      const state = useLiveStreamStore.getState();
      const box = el.getBoundingClientRect();
      const next = clampViewerPan(
        {x: state.viewPanX + dx, y: state.viewPanY + dy},
        state.viewZoomScale,
        box.width,
        box.height,
      );
      setViewPan(next.x, next.y);
    };

    const onUp = (event: PointerEvent) => {
      pointers.delete(event.pointerId);
      if (event.pointerId === pointerId) {
        dragging = false;
      }
    };

    el.addEventListener('pointerdown', onDown);
    el.addEventListener('pointermove', onMove);
    el.addEventListener('pointerup', onUp);
    el.addEventListener('pointercancel', onUp);
    return () => {
      el.removeEventListener('pointerdown', onDown);
      el.removeEventListener('pointermove', onMove);
      el.removeEventListener('pointerup', onUp);
      el.removeEventListener('pointercancel', onUp);
    };
  }, [sessionId, streamType, setViewPan]);

  useEffect(() => {
    if (!sessionId || streamType === 'AUDIO') {
      return;
    }
    const box = videoWrapRef.current?.getBoundingClientRect();
    const next = clampViewerPan(
      {x: viewPanX, y: viewPanY},
      viewZoomScale,
      box?.width || wrapSize.w,
      box?.height || wrapSize.h,
    );
    if (next.x !== viewPanX || next.y !== viewPanY) {
      setViewPan(next.x, next.y);
    }
  }, [sessionId, streamType, viewZoomScale, viewPanX, viewPanY, wrapSize.w, wrapSize.h, setViewPan]);

  if (!sessionId || streamType === 'AUDIO') {
    return streamType === 'AUDIO' ? (
      <p className="muted center-overlay">Audio-only stream active</p>
    ) : null;
  }

  const cameraObjectFit = isCameraLive ? webCameraObjectFit(cameraFacing) : 'contain';
  const viewPan = clampViewerPan(
    {x: viewPanX, y: viewPanY},
    viewZoomScale,
    wrapSize.w || 640,
    wrapSize.h || 400,
  );
  const videoStyle: CSSProperties | undefined = viewerVideoRotateStyle(
    combinedViewerRotate,
    wrapSize.w || 640,
    wrapSize.h || 400,
    isCameraLive ? cameraObjectFit : 'contain',
    viewZoomScale,
    viewPan,
  );
  const videoClassName = ['live-video', isCameraLive ? 'live-video--camera' : '']
    .filter(Boolean)
    .join(' ');
  const cameraLandscape =
    isCameraLive && videoFrame.w > 0 && videoFrame.h > 0 && videoFrame.w > videoFrame.h;

  const [isFullscreen, setIsFullscreen] = useState(false);
  const [waitingForVideo, setWaitingForVideo] = useState(false);

  const hasVideoTrack = Boolean(remoteStream?.getVideoTracks().length);
  const hasVideoFrame = videoFrame.w > 0 && videoFrame.h > 0;

  useEffect(() => {
    if (!isCameraLive || !sessionId) {
      setWaitingForVideo(false);
      return;
    }
    if (hasVideoFrame) {
      setWaitingForVideo(false);
      return;
    }
    const timer = window.setTimeout(() => setWaitingForVideo(true), 1500);
    return () => window.clearTimeout(timer);
  }, [isCameraLive, sessionId, hasVideoFrame, remoteStream]);

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
        viewZoomScale > 1 ? 'video-wrap--zoomed' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      title={viewZoomScale > 1 ? 'Drag to see the rest of the zoomed picture' : undefined}>
      {isCameraLive ? (
        <CameraFrameViewer
          stream={remoteStream}
          objectFit={cameraObjectFit}
          canvasClassName={videoClassName}
          viewerRotate={effectiveViewRotate}
          viewZoomScale={viewZoomScale}
          viewPan={viewPan}
          facingHint={cameraFacing}
          onFacingInferred={onFacingInferred}
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
      {isCameraLive && waitingForVideo && !hasVideoFrame ? (
        <p className="muted center-overlay">Waiting for camera feed…</p>
      ) : null}
      {isCameraLive && !hasVideoTrack && !waitingForVideo ? (
        <p className="muted center-overlay">Connecting camera…</p>
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
  const viewZoomScale = useLiveStreamStore(s => s.viewZoomScale);
  const setViewRotate = useLiveStreamStore(s => s.setViewRotate);
  const setViewZoomScale = useLiveStreamStore(s => s.setViewZoomScale);
  const resetView = useLiveStreamStore(s => s.resetView);
  const expandPip = useLiveStreamStore(s => s.expandPip);
  const resetPipLayout = useLiveStreamStore(s => s.resetPipLayout);
  const toggleVideoFullscreen = useLiveStreamStore(s => s.toggleVideoFullscreen);
  const stop = useLiveStreamStore(s => s.stop);
  const flipCamera = useLiveStreamStore(s => s.flipCamera);
  const toggleMute = useLiveStreamStore(s => s.toggleMute);

  const showLayout = compact && !layoutInToolbar;
  const showRotate = streamType !== 'AUDIO' && ENABLE_VIEWER_ROTATE;
  const showZoom = streamType !== 'AUDIO';

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
      {!compact && streamType !== 'AUDIO' ? (
        <button type="button" className="btn ghost sm" onClick={() => void openMeetStylePip()}>
          Picture in picture
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
      {showZoom ? (
        <>
          <button
            type="button"
            className="btn ghost sm"
            onClick={() =>
              setViewZoomScale(s => nudgeViewerZoomPercent(s, -VIEWER_ZOOM_STEP_PERCENT))
            }>
            −{VIEWER_ZOOM_STEP_PERCENT}%
          </button>
          <span className="stream-zoom-label">{formatViewerZoomLabel(viewZoomScale)}</span>
          <button
            type="button"
            className="btn ghost sm"
            onClick={() =>
              setViewZoomScale(s => nudgeViewerZoomPercent(s, VIEWER_ZOOM_STEP_PERCENT))
            }>
            +{VIEWER_ZOOM_STEP_PERCENT}%
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
  const streamType = useLiveStreamStore(s => s.streamType);
  const viewRotate = useLiveStreamStore(s => s.viewRotate);
  const setViewRotate = useLiveStreamStore(s => s.setViewRotate);
  const resetView = useLiveStreamStore(s => s.resetView);
  const expandPip = useLiveStreamStore(s => s.expandPip);
  const resetPipLayout = useLiveStreamStore(s => s.resetPipLayout);
  const showRotate = streamType !== 'AUDIO' && ENABLE_VIEWER_ROTATE;

  return (
    <div className="stream-pip-toolbar">
      <button type="button" className="btn ghost sm" onClick={expandPip}>
        Large
      </button>
      {showRotate ? (
        <button
          type="button"
          className="btn ghost sm"
          onClick={() => setViewRotate(deg => nextViewerRotateDeg(deg))}>
          Rotate {viewRotate}°
        </button>
      ) : null}
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
