import type {ViewStyle} from 'react-native';

/** Clockwise viewer-only rotation (UI transform; does not move the child camera). */
export type ViewerRotateDeg = 0 | 90 | 180 | 270;

export const MIN_VIEWER_PINCH = 1;
export const MAX_VIEWER_PINCH = 4;
export const MIN_VIEWER_ZOOM_PERCENT = 100;
export const MAX_VIEWER_ZOOM_PERCENT = 400;
/** Button step and display granularity for viewer zoom. */
export const VIEWER_ZOOM_STEP_PERCENT = 10;

export function scaleToViewerZoomPercent(scale: number): number {
  const clamped = clampViewerPinchScale(scale);
  return Math.round(clamped * 100);
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

/** e.g. 100% at 1×, 150% at 1.5× */
export function formatViewerZoomLabel(scale: number): string {
  return `${scaleToViewerZoomPercent(scale)}%`;
}

/** Step zoom by ±10% (or other step) for +/- buttons. */
export function nudgeViewerZoomPercent(currentScale: number, deltaPercent: number): number {
  return viewerZoomPercentToScale(scaleToViewerZoomPercent(currentScale) + deltaPercent);
}

export function nextViewerRotateDeg(current: ViewerRotateDeg): ViewerRotateDeg {
  return ((current + 90) % 360) as ViewerRotateDeg;
}

export function prevViewerRotateDeg(current: ViewerRotateDeg): ViewerRotateDeg {
  return ((current + 270) % 360) as ViewerRotateDeg;
}

/** Scale that keeps a 90°/270° rotation inside the container (letterbox, not zoom/crop). */
export function viewerRotateFitScale(
  degrees: ViewerRotateDeg,
  containerW: number,
  containerH: number,
): number {
  if (degrees === 0 || degrees === 180) {
    return 1;
  }
  const w = Math.max(1, containerW);
  const h = Math.max(1, containerH);
  return Math.min(w / h, h / w);
}

export function clampViewerPinchScale(scale: number): number {
  if (!Number.isFinite(scale)) {
    return MIN_VIEWER_PINCH;
  }
  return Math.min(MAX_VIEWER_PINCH, Math.max(MIN_VIEWER_PINCH, scale));
}

export function nextPinchScale(
  startScale: number,
  startDistance: number,
  currentDistance: number,
): number {
  if (startDistance <= 0 || currentDistance <= 0) {
    return clampViewerPinchScale(startScale);
  }
  return clampViewerPinchScale(startScale * (currentDistance / startDistance));
}

/** Button zoom: multiply current scale (e.g. 1.25 in, 0.8 out). */
export function nudgePinchScale(current: number, factor: number): number {
  return clampViewerPinchScale(current * factor);
}

/** Layout box for Android viewer zoom + rotate (parent-side only). */
export function androidViewerVideoLayout(
  degrees: ViewerRotateDeg,
  containerW: number,
  containerH: number,
  pinchScale = 1,
): ViewStyle {
  const pinch = clampViewerPinchScale(pinchScale);
  const w = Math.max(1, containerW);
  const h = Math.max(1, containerH);

  // Android 90° is applied by rotating WebRTC frames (not View transforms / W-H swap).
  // Swapping W/H without pixel rotation letterboxes an upright image and looks "not rotated".
  const boxW = w * pinch;
  const boxH = h * pinch;
  return {
    position: 'absolute',
    width: boxW,
    height: boxH,
    left: (w - boxW) / 2,
    top: (h - boxH) / 2,
  };
}

/**
 * Rotate in place and letterbox 90°/270°. Optional pinch scale is multiplied on top
 * so zoom is a separate gesture from rotate.
 */
export function rnViewerVideoRotateStyle(
  degrees: ViewerRotateDeg,
  containerW: number,
  containerH: number,
  pinchScale = 1,
): ViewStyle {
  const pinch = clampViewerPinchScale(pinchScale);
  const fit = viewerRotateFitScale(degrees, containerW, containerH);
  const scale = fit * pinch;
  const transform: NonNullable<ViewStyle['transform']> | undefined =
    degrees !== 0 || scale !== 1
      ? ([
          ...(degrees !== 0 ? [{rotate: `${degrees}deg`}] : []),
          ...(scale !== 1 ? [{scale}] : []),
        ] as unknown as NonNullable<ViewStyle['transform']>)
      : undefined;
  return {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    ...(transform ? {transform} : {}),
  };
}
