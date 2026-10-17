import type {ViewerRotateDeg} from './viewerVideoRotate';

function normalizeDegrees(degrees: number): ViewerRotateDeg {
  return (((Math.round(degrees) % 360) + 360) % 360) as ViewerRotateDeg;
}

/**
 * Rotation the parent viewer should apply (iOS). Android uses CameraFrameRotation natively.
 * Child bakes device tilt into CameraX pixels; CVO is always 0 for front camera.
 */
export function cameraUprightRotateDeg(
  _cvo: number = 0,
  manualRotate: ViewerRotateDeg = 0,
  frontCamera = true,
): ViewerRotateDeg {
  if (!frontCamera) {
    return normalizeDegrees(_cvo + manualRotate);
  }
  return normalizeDegrees(manualRotate);
}
