/** Clockwise viewer-only rotation for the web live video (CSS; does not move the child camera). */
export type ViewerRotateDeg = 0 | 90 | 180 | 270;

export const MIN_VIEWER_PINCH = 1;
export const MAX_VIEWER_PINCH = 3;
export const MIN_VIEWER_ZOOM_PERCENT = 100;
export const MAX_VIEWER_ZOOM_PERCENT = 300;
/** Button step and display granularity for viewer zoom. */
export const VIEWER_ZOOM_STEP_PERCENT = 5;
/** Lower = slower pinch zoom (0.4 → a 2× finger spread ≈ 1.4× video zoom). */
export const VIEWER_PINCH_SENSITIVITY = 0.4;

export function clampViewerPinchScale(scale: number): number {
  if (!Number.isFinite(scale)) {
    return MIN_VIEWER_PINCH;
  }
  return Math.min(MAX_VIEWER_PINCH, Math.max(MIN_VIEWER_PINCH, scale));
}

export function scaleToViewerZoomPercent(scale: number): number {
  return Math.round(clampViewerPinchScale(scale) * 100);
}

export function viewerZoomPercentToScale(percent: number): number {
  if (!Number.isFinite(percent)) {
    return MIN_VIEWER_PINCH;
  }
  const clamped = Math.min(
    MAX_VIEWER_ZOOM_PERCENT,
    Math.max(MIN_VIEWER_ZOOM_PERCENT, Math.round(percent)),
  );
  return clamped / 100;
}

export function formatViewerZoomLabel(scale: number): string {
  return `${scaleToViewerZoomPercent(scale)}%`;
}

export function nudgeViewerZoomPercent(currentScale: number, deltaPercent: number): number {
  return viewerZoomPercentToScale(scaleToViewerZoomPercent(currentScale) + deltaPercent);
}

export function nextPinchScale(
  startScale: number,
  startDistance: number,
  currentDistance: number,
  sensitivity: number = VIEWER_PINCH_SENSITIVITY,
): number {
  if (startDistance <= 0 || currentDistance <= 0) {
    return clampViewerPinchScale(startScale);
  }
  const rawRatio = currentDistance / startDistance;
  const dampedRatio = 1 + (rawRatio - 1) * sensitivity;
  return clampViewerPinchScale(startScale * dampedRatio);
}

export function nextViewerRotateDeg(current: ViewerRotateDeg): ViewerRotateDeg {
  return ((current + 90) % 360) as ViewerRotateDeg;
}

export function prevViewerRotateDeg(current: ViewerRotateDeg): ViewerRotateDeg {
  return ((current + 270) % 360) as ViewerRotateDeg;
}

/** Aspect ratio for the viewer box after applying viewer-only rotation. */
export function viewerContainerAspect(
  videoWidth: number,
  videoHeight: number,
  degrees: ViewerRotateDeg,
): number | null {
  if (videoWidth <= 0 || videoHeight <= 0) {
    return null;
  }
  if (degrees === 90 || degrees === 270) {
    return videoHeight / videoWidth;
  }
  return videoWidth / videoHeight;
}

export type ViewerVideoStyle = {
  transform: string;
  width: number | string;
  height: number | string;
  position: 'absolute';
  inset?: number | string;
  left?: string;
  top?: string;
  marginLeft?: number;
  marginTop?: number;
  objectFit: 'contain' | 'cover';
};

/**
 * Layout style so a CSS-rotated video still fits the container without clipping.
 */
function viewerZoomTransform(degrees: ViewerRotateDeg, pinchScale: number): string {
  const pinch = clampViewerPinchScale(pinchScale);
  const parts: string[] = [];
  if (degrees !== 0) {
    parts.push(`rotate(${degrees}deg)`);
  }
  if (pinch !== 1) {
    parts.push(`scale(${pinch})`);
  }
  return parts.length > 0 ? parts.join(' ') : 'none';
}

export function viewerZoomLayerStyle(pinchScale: number): {transform: string} {
  const pinch = clampViewerPinchScale(pinchScale);
  return {
    transform: pinch === 1 ? 'none' : `scale(${pinch})`,
  };
}

export function viewerVideoRotateStyle(
  degrees: ViewerRotateDeg,
  containerW: number,
  containerH: number,
  objectFit: 'contain' | 'cover' = 'contain',
  pinchScale = 1,
): ViewerVideoStyle {
  const transform = viewerZoomTransform(degrees, pinchScale);
  if (degrees === 0) {
    return {
      transform,
      width: '100%',
      height: '100%',
      position: 'absolute',
      inset: 0,
      objectFit,
    };
  }
  if (degrees === 180) {
    return {
      transform,
      width: '100%',
      height: '100%',
      position: 'absolute',
      inset: 0,
      objectFit,
    };
  }
  const w = Math.max(1, containerH);
  const h = Math.max(1, containerW);
  return {
    transform,
    width: w,
    height: h,
    position: 'absolute',
    left: '50%',
    top: '50%',
    marginLeft: -w / 2,
    marginTop: -h / 2,
    objectFit,
  };
}
