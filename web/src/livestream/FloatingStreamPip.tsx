import {useRef, useState, useEffect, type PointerEvent as ReactPointerEvent, type TouchEvent as ReactTouchEvent} from 'react';
import {createPortal} from 'react-dom';
import {useLocation} from 'react-router-dom';
import {useLiveStreamStore} from './liveStreamStore';
import {
  clampPipPosition,
  isPipDragBlockedTarget,
  startPipDragSession,
  type PipDragSession,
} from './pipDrag';
import {
  getPipSizeLimits,
  isMobilePipViewport,
  pipSizeForAspect,
  pipSizeFromBodyHeight,
  pipSizeFromWidth,
  pipVideoAspect,
} from './pipLayout';
import {PipDeviceLink, StreamControls, StreamPipToolbar, StreamVideoView} from './StreamVideoView';

/** Header + toolbar + footer (px) — body height is resized separately. */
const PIP_CHROME_HEIGHT = 152;
const PIP_CHROME_HEIGHT_MOBILE = 108;
const DOCK_INSET = 12;

type DragState = {
  originX: number;
  originY: number;
};

type ResizeCorner = 'nw' | 'ne' | 'sw' | 'se' | 'n' | 's' | 'e' | 'w';

type ResizeState = {
  startX: number;
  startY: number;
  originX: number;
  originY: number;
  originWidth: number;
  originBodyHeight: number;
  corner: ResizeCorner;
};

const CORNER_HANDLES: Array<{corner: ResizeCorner; className: string; label: string}> = [
  {corner: 'nw', className: 'stream-pip-resize-nw', label: 'Resize from top left'},
  {corner: 'ne', className: 'stream-pip-resize-ne', label: 'Resize from top right'},
  {corner: 'sw', className: 'stream-pip-resize-sw', label: 'Resize from bottom left'},
  {corner: 'se', className: 'stream-pip-resize-se', label: 'Resize from bottom right'},
];

const EDGE_HANDLES: Array<{corner: ResizeCorner; className: string; label: string}> = [
  {corner: 'n', className: 'stream-pip-resize-n', label: 'Resize height from top'},
  {corner: 's', className: 'stream-pip-resize-s', label: 'Resize height from bottom'},
  {corner: 'w', className: 'stream-pip-resize-w', label: 'Resize width from left'},
  {corner: 'e', className: 'stream-pip-resize-e', label: 'Resize width from right'},
];

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function panelHeight(bodyHeight: number) {
  const chrome = isMobilePipViewport() ? PIP_CHROME_HEIGHT_MOBILE : PIP_CHROME_HEIGHT;
  return bodyHeight + chrome;
}

function applyPipResize(
  corner: ResizeCorner,
  dx: number,
  dy: number,
  origin: ResizeState,
  aspect: number,
): {
  width: number;
  bodyHeight: number;
  x: number;
  y: number;
} {
  const limits = getPipSizeLimits();
  const widthSign = corner.includes('w') ? -1 : corner.includes('e') ? 1 : 0;
  const heightSign = corner.includes('n') ? -1 : corner.includes('s') ? 1 : 0;

  let nextWidth = origin.originWidth;
  let nextBodyHeight = origin.originBodyHeight;

  if (widthSign !== 0) {
    nextWidth = clamp(origin.originWidth + widthSign * dx, limits.minWidth, limits.maxWidth);
  }
  if (heightSign !== 0) {
    nextBodyHeight = clamp(
      origin.originBodyHeight + heightSign * dy,
      limits.minBodyHeight,
      limits.maxBodyHeight,
    );
  }

  let size =
    widthSign !== 0 && heightSign !== 0
      ? (() => {
          const fromWidth = pipSizeFromWidth(nextWidth, aspect);
          const fromHeight = pipSizeFromBodyHeight(nextBodyHeight, aspect);
          const widthDelta = Math.abs(fromWidth.width - origin.originWidth);
          const heightDelta = Math.abs(fromHeight.bodyHeight - origin.originBodyHeight);
          return widthDelta >= heightDelta ? fromWidth : fromHeight;
        })()
      : widthSign !== 0
        ? pipSizeFromWidth(nextWidth, aspect)
        : pipSizeFromBodyHeight(nextBodyHeight, aspect);

  let x = origin.originX;
  let y = origin.originY;

  if (corner.includes('w')) {
    x = origin.originX + (origin.originWidth - size.width);
  }
  if (corner.includes('n')) {
    y = origin.originY + (origin.originBodyHeight - size.bodyHeight);
  }

  const maxX = Math.max(8, window.innerWidth - size.width - 8);
  const maxY = Math.max(8, window.innerHeight - panelHeight(size.bodyHeight) - 8);
  x = clamp(x, 8, maxX);
  y = clamp(y, 8, maxY);

  if (corner.includes('w')) {
    size = {...size, width: Math.min(size.width, origin.originX + origin.originWidth - x)};
    size = pipSizeFromWidth(clamp(size.width, limits.minWidth, limits.maxWidth), aspect);
  }
  if (corner.includes('n')) {
    size = {
      ...size,
      bodyHeight: Math.min(size.bodyHeight, origin.originY + origin.originBodyHeight - y),
    };
    size = pipSizeFromBodyHeight(
      clamp(size.bodyHeight, limits.minBodyHeight, limits.maxBodyHeight),
      aspect,
    );
  }

  return {width: size.width, bodyHeight: size.bodyHeight, x, y};
}

function PipResizeHandles({onResizeStart}: {onResizeStart: (corner: ResizeCorner) => (event: ReactPointerEvent) => void}) {
  return (
    <>
      {EDGE_HANDLES.map(handle => (
        <div
          key={handle.className}
          className={`stream-pip-resize ${handle.className}`}
          role="separator"
          aria-label={handle.label}
          onPointerDown={onResizeStart(handle.corner)}
        />
      ))}
      {CORNER_HANDLES.map(handle => (
        <div
          key={handle.className}
          className={`stream-pip-resize stream-pip-resize-corner ${handle.className}`}
          role="separator"
          aria-label={handle.label}
          onPointerDown={onResizeStart(handle.corner)}
        />
      ))}
    </>
  );
}

export function FloatingStreamPip() {
  const location = useLocation();
  const sessionId = useLiveStreamStore(s => s.sessionId);
  const deviceId = useLiveStreamStore(s => s.deviceId);
  const deviceName = useLiveStreamStore(s => s.deviceName);
  const status = useLiveStreamStore(s => s.status);
  const pipMinimized = useLiveStreamStore(s => s.pipMinimized);
  const pipPosition = useLiveStreamStore(s => s.pipPosition);
  const pipSize = useLiveStreamStore(s => s.pipSize);
  const videoFrame = useLiveStreamStore(s => s.videoFrame);
  const viewRotate = useLiveStreamStore(s => s.viewRotate);
  const streamType = useLiveStreamStore(s => s.streamType);
  const setPipMinimized = useLiveStreamStore(s => s.setPipMinimized);
  const setPipPosition = useLiveStreamStore(s => s.setPipPosition);
  const setPipSize = useLiveStreamStore(s => s.setPipSize);
  const stop = useLiveStreamStore(s => s.stop);

  const dragRef = useRef<DragState | null>(null);
  const dragSessionRef = useRef<PipDragSession | null>(null);
  const resizeRef = useRef<ResizeState | null>(null);
  const videoAspectRef = useRef(16 / 10);
  const [dragging, setDragging] = useState(false);
  const [resizing, setResizing] = useState(false);
  const pipSizedForFrame = useRef(false);
  const leftDevicePageRef = useRef(false);
  const panelSizeRef = useRef({width: 320, height: 288});

  const videoAspect = pipVideoAspect(videoFrame, viewRotate, streamType);
  videoAspectRef.current = videoAspect;

  const onActiveDevicePage =
    deviceId != null && location.pathname === `/devices/${deviceId}`;

  useEffect(() => {
    if (onActiveDevicePage) {
      leftDevicePageRef.current = false;
      return;
    }
    if (!sessionId || leftDevicePageRef.current || !isMobilePipViewport()) {
      return;
    }
    leftDevicePageRef.current = true;
    pipSizedForFrame.current = true;
    setPipSize(pipSizeForAspect(videoAspect, getPipSizeLimits().default));
    setPipPosition(null);
  }, [
    onActiveDevicePage,
    sessionId,
    videoAspect,
    setPipSize,
    setPipPosition,
  ]);

  useEffect(() => {
    if (videoFrame.w <= 0 || videoFrame.h <= 0 || pipSizedForFrame.current) {
      return;
    }
    pipSizedForFrame.current = true;
    const limits = getPipSizeLimits();
    const seed = isMobilePipViewport()
      ? limits.default
      : {width: pipSize.width, bodyHeight: pipSize.bodyHeight};
    setPipSize(pipSizeFromWidth(seed.width, videoAspect));
  }, [videoFrame.w, videoFrame.h, videoAspect, pipSize.width, setPipSize]);

  useEffect(() => {
    if (!sessionId) {
      pipSizedForFrame.current = false;
      leftDevicePageRef.current = false;
    }
  }, [sessionId]);

  useEffect(() => {
    if (videoFrame.w <= 0 && videoFrame.h <= 0) {
      pipSizedForFrame.current = false;
    }
  }, [videoFrame.w, videoFrame.h]);

  const prevViewRotate = useRef(viewRotate);
  useEffect(() => {
    if (prevViewRotate.current === viewRotate || videoFrame.w <= 0) {
      prevViewRotate.current = viewRotate;
      return;
    }
    prevViewRotate.current = viewRotate;
    setPipSize(pipSizeFromWidth(pipSize.width, videoAspect));
  }, [viewRotate, videoAspect, videoFrame.w, pipSize.width, setPipSize]);

  useEffect(() => {
    return () => {
      dragSessionRef.current?.cleanup();
      dragSessionRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!resizing) {
      return;
    }
    const onMove = (event: PointerEvent) => {
      const state = resizeRef.current;
      if (!state) {
        return;
      }
      event.preventDefault();
      const dx = event.clientX - state.startX;
      const dy = event.clientY - state.startY;
      const next = applyPipResize(state.corner, dx, dy, state, videoAspectRef.current);
      setPipSize({width: next.width, bodyHeight: next.bodyHeight});
      setPipPosition({x: next.x, y: next.y});
    };
    const onEnd = () => {
      resizeRef.current = null;
      setResizing(false);
    };
    window.addEventListener('pointermove', onMove, {passive: false});
    window.addEventListener('pointerup', onEnd);
    window.addEventListener('pointercancel', onEnd);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
    };
  }, [resizing, setPipPosition, setPipSize]);

  if (!sessionId || onActiveDevicePage) {
    return null;
  }

  const mobile = isMobilePipViewport();
  const panelWidth = pipSize.width;
  const panelHeightPx = panelHeight(pipSize.bodyHeight);
  panelSizeRef.current = {width: panelWidth, height: panelHeightPx};
  const defaultX = mobile
    ? Math.max(8, window.innerWidth - panelWidth - 12)
    : window.innerWidth - panelWidth - 24;
  const defaultY = mobile
    ? Math.max(8, window.innerHeight - panelHeightPx - 72)
    : window.innerHeight - panelHeightPx - 24;
  const x = pipPosition?.x ?? defaultX;
  const y = pipPosition?.y ?? defaultY;

  const beginDrag = (
    clientX: number,
    clientY: number,
    pointerId?: number,
    captureTarget?: HTMLElement | null,
  ) => {
    dragSessionRef.current?.cleanup();
    dragRef.current = {originX: x, originY: y};
    setDragging(true);
    dragSessionRef.current = startPipDragSession({
      startX: clientX,
      startY: clientY,
      originX: x,
      originY: y,
      pointerId,
      captureTarget,
      onMove: (dx, dy) => {
        const origin = dragRef.current;
        if (!origin) {
          return;
        }
        const next = clampPipPosition(
          origin.originX + dx,
          origin.originY + dy,
          panelSizeRef.current.width,
          panelSizeRef.current.height,
        );
        setPipPosition(next);
      },
      onEnd: () => {
        dragRef.current = null;
        dragSessionRef.current = null;
        setDragging(false);
      },
    });
  };

  const onDragPointerDown = (event: ReactPointerEvent) => {
    if (event.button !== 0) {
      return;
    }
    if (mobile && event.pointerType === 'touch') {
      return;
    }
    if (isPipDragBlockedTarget(event.target)) {
      return;
    }
    beginDrag(event.clientX, event.clientY, event.pointerId, event.currentTarget as HTMLElement);
    event.preventDefault();
    event.stopPropagation();
  };

  const onDragTouchStart = (event: ReactTouchEvent) => {
    if (!mobile || event.touches.length !== 1) {
      return;
    }
    if (isPipDragBlockedTarget(event.target)) {
      return;
    }
    const touch = event.touches[0];
    beginDrag(touch.clientX, touch.clientY);
    event.preventDefault();
    event.stopPropagation();
  };

  const onResizeStart =
    (corner: ResizeCorner) => (event: ReactPointerEvent) => {
      if (event.button !== 0) {
        return;
      }
      event.stopPropagation();
      event.preventDefault();
      (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
      resizeRef.current = {
        startX: event.clientX,
        startY: event.clientY,
        originX: x,
        originY: y,
        originWidth: pipSize.width,
        originBodyHeight: pipSize.bodyHeight,
        corner,
      };
      setResizing(true);
    };

  const minimize = () => setPipMinimized(true);
  const expand = () => setPipMinimized(false);
  const closeStream = () => void stop();

  if (pipMinimized) {
    const dock = (
      <div
        className="stream-pip-dock stream-pip-dock--mobile"
        style={{
          top: mobile ? undefined : DOCK_INSET,
          bottom: mobile ? DOCK_INSET + 56 : undefined,
          right: DOCK_INSET,
        }}>
        <button
          type="button"
          className="stream-pip-dock-main"
          aria-label={`Restore live stream for ${deviceName ?? 'device'}`}
          onClick={expand}>
          <span className="stream-pip-dot" />
          <span className="stream-pip-dock-title">{deviceName ?? 'Live stream'}</span>
          <span className="stream-pip-dock-live">LIVE</span>
        </button>
        <button
          type="button"
          className="stream-pip-head-btn stream-pip-close"
          aria-label="Close live stream"
          onClick={closeStream}>
          ×
        </button>
      </div>
    );
    return createPortal(dock, document.body);
  }

  const pip = (
    <div
      className={`stream-pip${mobile ? ' stream-pip--mobile' : ''}${dragging ? ' dragging' : ''}${resizing ? ' resizing' : ''}`}
      style={{
        left: x,
        top: y,
        width: pipSize.width,
      }}
      onPointerDownCapture={mobile ? onDragPointerDown : undefined}
      onTouchStartCapture={mobile ? onDragTouchStart : undefined}>
      <header
        className="stream-pip-head"
        onPointerDown={mobile ? undefined : onDragPointerDown}>
        <span className="stream-pip-dot" />
        <span className="stream-pip-title">{deviceName ?? 'Live stream'}</span>
        <span className="stream-pip-status">{status}</span>
        <div className="stream-pip-head-actions">
          <button
            type="button"
            className="stream-pip-head-btn stream-pip-minimize"
            aria-label="Minimize to top right"
            onPointerDown={event => event.stopPropagation()}
            onClick={event => {
              event.stopPropagation();
              minimize();
            }}>
            −
          </button>
          <button
            type="button"
            className="stream-pip-head-btn stream-pip-close"
            aria-label="Close live stream"
            onPointerDown={event => event.stopPropagation()}
            onClick={event => {
              event.stopPropagation();
              closeStream();
            }}>
            ×
          </button>
        </div>
      </header>
      <StreamPipToolbar />
      <div className="stream-pip-body" style={{height: pipSize.bodyHeight}}>
        <StreamVideoView compact />
      </div>
      <div className="stream-pip-foot">
        {!mobile ? <PipDeviceLink /> : null}
        <StreamControls compact layoutInToolbar={!mobile} />
      </div>
      {!mobile ? <PipResizeHandles onResizeStart={onResizeStart} /> : null}
    </div>
  );

  return createPortal(pip, document.body);
}
