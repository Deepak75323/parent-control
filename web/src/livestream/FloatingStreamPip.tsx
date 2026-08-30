import {useRef, useState, useEffect, type PointerEvent as ReactPointerEvent} from 'react';
import {useLocation} from 'react-router-dom';
import {useLiveStreamStore, PIP_SIZE_LIMITS} from './liveStreamStore';
import {pipSizeFromBodyHeight, pipSizeFromWidth, pipVideoAspect} from './pipLayout';
import {PipDeviceLink, StreamControls, StreamPipToolbar, StreamVideoView} from './StreamVideoView';

const PIP_MIN_WIDTH = PIP_SIZE_LIMITS.minWidth;
const PIP_MAX_WIDTH = PIP_SIZE_LIMITS.maxWidth;
const PIP_MIN_BODY = PIP_SIZE_LIMITS.minBodyHeight;
const PIP_MAX_BODY = PIP_SIZE_LIMITS.maxBodyHeight;
/** Header + toolbar + footer (px) — body height is resized separately. */
const PIP_CHROME_HEIGHT = 152;
const DOCK_INSET = 12;

type DragState = {
  startX: number;
  startY: number;
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
  return bodyHeight + PIP_CHROME_HEIGHT;
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
  const widthSign = corner.includes('w') ? -1 : corner.includes('e') ? 1 : 0;
  const heightSign = corner.includes('n') ? -1 : corner.includes('s') ? 1 : 0;

  let nextWidth = origin.originWidth;
  let nextBodyHeight = origin.originBodyHeight;

  if (widthSign !== 0) {
    nextWidth = clamp(origin.originWidth + widthSign * dx, PIP_MIN_WIDTH, PIP_MAX_WIDTH);
  }
  if (heightSign !== 0) {
    nextBodyHeight = clamp(
      origin.originBodyHeight + heightSign * dy,
      PIP_MIN_BODY,
      PIP_MAX_BODY,
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
    size = pipSizeFromWidth(clamp(size.width, PIP_MIN_WIDTH, PIP_MAX_WIDTH), aspect);
  }
  if (corner.includes('n')) {
    size = {
      ...size,
      bodyHeight: Math.min(size.bodyHeight, origin.originY + origin.originBodyHeight - y),
    };
    size = pipSizeFromBodyHeight(clamp(size.bodyHeight, PIP_MIN_BODY, PIP_MAX_BODY), aspect);
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
  const resizeRef = useRef<ResizeState | null>(null);
  const videoAspectRef = useRef(16 / 10);
  const [dragging, setDragging] = useState(false);
  const [resizing, setResizing] = useState(false);
  const pipSizedForFrame = useRef(false);

  const videoAspect = pipVideoAspect(videoFrame, viewRotate, streamType);
  videoAspectRef.current = videoAspect;

  useEffect(() => {
    if (videoFrame.w <= 0 || videoFrame.h <= 0 || pipSizedForFrame.current) {
      return;
    }
    pipSizedForFrame.current = true;
    setPipSize(pipSizeFromWidth(pipSize.width, videoAspect));
  }, [videoFrame.w, videoFrame.h, videoAspect, pipSize.width, setPipSize]);

  useEffect(() => {
    if (!sessionId) {
      pipSizedForFrame.current = false;
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

  const onActiveDevicePage =
    deviceId != null && location.pathname === `/devices/${deviceId}`;

  if (!sessionId || onActiveDevicePage) {
    return null;
  }

  const panelWidth = pipSize.width;
  const panelHeightPx = panelHeight(pipSize.bodyHeight);
  const defaultX =
    typeof window !== 'undefined' ? window.innerWidth - panelWidth - 24 : 24;
  const defaultY =
    typeof window !== 'undefined' ? window.innerHeight - panelHeightPx - 24 : 24;
  const x = pipPosition?.x ?? defaultX;
  const y = pipPosition?.y ?? defaultY;

  const onDragPointerDown = (event: ReactPointerEvent) => {
    if (event.button !== 0) {
      return;
    }
    if ((event.target as HTMLElement).closest('button, a, .stream-pip-resize')) {
      return;
    }
    const target = event.currentTarget as HTMLElement;
    target.setPointerCapture(event.pointerId);
    dragRef.current = {
      startX: event.clientX,
      startY: event.clientY,
      originX: x,
      originY: y,
    };
    setDragging(true);
    event.preventDefault();
  };

  const onDragPointerMove = (event: ReactPointerEvent) => {
    if (!dragRef.current) {
      return;
    }
    const dx = event.clientX - dragRef.current.startX;
    const dy = event.clientY - dragRef.current.startY;
    const maxX = Math.max(8, window.innerWidth - panelWidth - 8);
    const maxY = Math.max(8, window.innerHeight - panelHeightPx - 8);
    setPipPosition({
      x: Math.min(maxX, Math.max(8, dragRef.current.originX + dx)),
      y: Math.min(maxY, Math.max(8, dragRef.current.originY + dy)),
    });
  };

  const endDrag = (event: ReactPointerEvent) => {
    dragRef.current = null;
    setDragging(false);
    try {
      (event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId);
    } catch {
      /* ignore */
    }
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
    return (
      <div
        className="stream-pip-dock"
        style={{top: DOCK_INSET, right: DOCK_INSET}}>
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
  }

  return (
    <div
      className={`stream-pip${dragging ? ' dragging' : ''}${resizing ? ' resizing' : ''}`}
      style={{
        left: x,
        top: y,
        width: pipSize.width,
      }}>
      <header
        className="stream-pip-head"
        onPointerDown={onDragPointerDown}
        onPointerMove={onDragPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}>
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
        <PipDeviceLink />
        <StreamControls compact layoutInToolbar />
      </div>
      <PipResizeHandles onResizeStart={onResizeStart} />
    </div>
  );
}
