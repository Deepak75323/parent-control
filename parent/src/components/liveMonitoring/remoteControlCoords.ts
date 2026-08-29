import {MediaStream} from 'react-native-webrtc';

/** Derive contain-fit aspect from the remote video track when available. */
export function videoAspectFromStream(stream: MediaStream | null, fallback = 9 / 16): number {
  const track = stream?.getVideoTracks()[0];
  if (!track) return fallback;
  const settings = (track as {getSettings?: () => {width?: number; height?: number}}).getSettings?.();
  const width = settings?.width ?? 0;
  const height = settings?.height ?? 0;
  if (width > 0 && height > 0) {
    return width / height;
  }
  return fallback;
}

/**
 * Aspect ratio for layout on the parent viewer. Mobile camera buffers are usually
 * landscape (e.g. 1280×960) while the phone is held portrait — swap when needed.
 */
export function displayVideoAspectFromFrameSize(
  width: number,
  height: number,
  mobileCamera = false,
): number {
  if (width <= 0 || height <= 0) {
    return 9 / 16;
  }
  if (mobileCamera && width > height) {
    return height / width;
  }
  return width / height;
}

export function viewerVideoAspect(
  stream: MediaStream | null,
  streamType: 'SCREEN' | 'CAMERA' | 'AUDIO',
  frameSize: {width: number; height: number} | null,
): number {
  if (frameSize && frameSize.width > 0 && frameSize.height > 0) {
    return displayVideoAspectFromFrameSize(
      frameSize.width,
      frameSize.height,
      streamType === 'CAMERA',
    );
  }
  const fallback = streamType === 'CAMERA' ? 9 / 16 : 9 / 16;
  const raw = videoAspectFromStream(stream, fallback);
  if (streamType === 'CAMERA' && raw > 1) {
    return 1 / raw;
  }
  return raw;
}

/** Cover-fit render box — fills viewport, crops overflow (PreviewView FILL_CENTER). */
export function coverVideoLayoutBox(
  viewportW: number,
  viewportH: number,
  videoAspect: number,
  pinchScale = 1,
): {width: number; height: number; left: number; top: number} {
  if (viewportW <= 0 || viewportH <= 0 || videoAspect <= 0) {
    return {width: viewportW, height: viewportH, left: 0, top: 0};
  }
  const viewportAspect = viewportW / viewportH;
  let renderW: number;
  let renderH: number;
  if (viewportAspect > videoAspect) {
    renderW = viewportW;
    renderH = viewportW / videoAspect;
  } else {
    renderH = viewportH;
    renderW = viewportH * videoAspect;
  }
  const pinch = Number.isFinite(pinchScale) ? Math.max(1, pinchScale) : 1;
  renderW *= pinch;
  renderH *= pinch;
  return {
    width: renderW,
    height: renderH,
    left: (viewportW - renderW) / 2,
    top: (viewportH - renderH) / 2,
  };
}

/**
 * 1× uses contain (full frame, matches native FOV). Pinch zoom switches to cover fill.
 * Manual Android layout always pairs with RTCView objectFit contain to avoid double-crop.
 */
export function resolveCameraObjectFit(
  _frameSize: {width: number; height: number} | null,
  pinchScale: number,
): 'contain' | 'cover' {
  return pinchScale > 1 ? 'cover' : 'contain';
}

/** Letterboxed render box for object-fit: contain inside a viewport. */
export function containVideoLayoutBox(
  viewportW: number,
  viewportH: number,
  videoAspect: number,
  pinchScale = 1,
): {width: number; height: number; left: number; top: number} {
  if (viewportW <= 0 || viewportH <= 0 || videoAspect <= 0) {
    return {width: 1, height: 1, left: 0, top: 0};
  }
  const viewportAspect = viewportW / viewportH;
  let renderW: number;
  let renderH: number;
  if (viewportAspect > videoAspect) {
    renderH = viewportH;
    renderW = viewportH * videoAspect;
  } else {
    renderW = viewportW;
    renderH = viewportW / videoAspect;
  }
  const pinch = Number.isFinite(pinchScale) ? Math.max(1, pinchScale) : 1;
  renderW *= pinch;
  renderH *= pinch;
  return {
    width: renderW,
    height: renderH,
    left: (viewportW - renderW) / 2,
    top: (viewportH - renderH) / 2,
  };
}

/** Maps touch coords on a contain-fit video viewport to normalized 0–1 device coords. */
export function mapContainTouchToNorm(
  touchX: number,
  touchY: number,
  viewportW: number,
  viewportH: number,
  videoAspect = 9 / 16,
): {xNorm: number; yNorm: number} | null {
  if (viewportW <= 0 || viewportH <= 0) return null;
  const viewportAspect = viewportW / viewportH;
  let renderW: number;
  let renderH: number;
  let offsetX: number;
  let offsetY: number;
  if (viewportAspect > videoAspect) {
    renderH = viewportH;
    renderW = viewportH * videoAspect;
    offsetX = (viewportW - renderW) / 2;
    offsetY = 0;
  } else {
    renderW = viewportW;
    renderH = viewportW / videoAspect;
    offsetX = 0;
    offsetY = (viewportH - renderH) / 2;
  }
  const localX = touchX - offsetX;
  const localY = touchY - offsetY;
  if (localX < 0 || localY < 0 || localX > renderW || localY > renderH) {
    return null;
  }
  return {
    xNorm: localX / renderW,
    yNorm: localY / renderH,
  };
}
