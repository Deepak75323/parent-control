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
