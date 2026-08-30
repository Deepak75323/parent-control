import type {ViewerRotateDeg} from './viewerVideoRotate';

function normalizeDegrees(degrees: number): ViewerRotateDeg {
  return (((Math.round(degrees) % 360) + 360) % 360) as ViewerRotateDeg;
}

/**
 * Rotation the parent viewer should apply to remote camera frames.
 *
 * The child captures through CameraX, whose Preview use case bakes the target rotation into
 * the output surface, so pixels arrive already upright. The frame's CVO tag (270 in portrait)
 * is redundant — applying it a second time tips the image on its side, which is exactly what
 * the parent app did before. Only the viewer's manual rotate is applied.
 */
export function cameraUprightRotateDeg(
  manualRotate: ViewerRotateDeg = 0,
): ViewerRotateDeg {
  return normalizeDegrees(manualRotate);
}
