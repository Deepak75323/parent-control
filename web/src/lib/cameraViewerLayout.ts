/** Matches parent app camera facing convention. */
export type WebCameraFacing = 'user' | 'environment';

export function toggleCameraFacing(facing: WebCameraFacing): WebCameraFacing {
  return facing === 'user' ? 'environment' : 'user';
}

/**
 * Portrait display aspect for a phone camera buffer.
 * Browsers apply WebRTC CVO — use raw videoWidth/videoHeight (often landscape).
 */
export function webCameraDisplayAspect(videoWidth: number, videoHeight: number): number {
  if (videoWidth <= 0 || videoHeight <= 0) {
    return 3 / 4;
  }
  if (videoWidth > videoHeight) {
    return videoHeight / videoWidth;
  }
  return videoWidth / videoHeight;
}

/** Show the full camera frame — contain avoids cropping face/head at the edges. */
export function webCameraObjectFit(_facing: WebCameraFacing): 'contain' | 'cover' {
  return 'contain';
}
