import type {ViewStyle} from 'react-native';

/** Clockwise viewer-only rotation (UI transform; does not move the child camera). */
export type ViewerRotateDeg = 0 | 90 | 180 | 270;

export const MIN_VIEWER_PINCH = 1;
export const MAX_VIEWER_PINCH = 4;

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
  const transform: NonNullable<ViewStyle['transform']> = [];
  if (degrees !== 0) {
    transform.push({rotate: `${degrees}deg`});
  }
  if (scale !== 1) {
    transform.push({scale});
  }
  return {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    ...(transform.length > 0 ? {transform} : {}),
  };
}
