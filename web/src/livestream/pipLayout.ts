import type {StreamType} from '../types/api';
import {webCameraDisplayAspect} from '../lib/cameraViewerLayout';
import {viewerContainerAspect, type ViewerRotateDeg} from '../lib/viewerVideoRotate';

export const PIP_SIZE_LIMITS = {
  minWidth: 240,
  maxWidth: 720,
  minBodyHeight: 96,
  maxBodyHeight: 520,
  default: {width: 320, bodyHeight: 180},
} as const;

export function largePipSize() {
  if (typeof window === 'undefined') {
    return {width: 560, bodyHeight: 420};
  }
  return {
    width: Math.min(PIP_SIZE_LIMITS.maxWidth, window.innerWidth - 32),
    bodyHeight: Math.min(
      PIP_SIZE_LIMITS.maxBodyHeight,
      Math.max(320, Math.floor(window.innerHeight * 0.45)),
    ),
  };
}

/** Width ÷ height for the PiP video area (matches upright stream frame). */
export function pipVideoAspect(
  videoFrame: {w: number; h: number},
  viewRotate: ViewerRotateDeg,
  streamType: StreamType,
): number {
  if (videoFrame.w > 0 && videoFrame.h > 0) {
    const rotated = viewerContainerAspect(videoFrame.w, videoFrame.h, viewRotate);
    if (rotated != null && rotated > 0) {
      return rotated;
    }
  }
  if (streamType === 'CAMERA') {
    return webCameraDisplayAspect(videoFrame.w, videoFrame.h);
  }
  return 16 / 10;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

/** Fit a PiP panel size to limits while preserving video aspect ratio. */
export function pipSizeForAspect(
  aspect: number,
  seed: {width: number; bodyHeight: number} = PIP_SIZE_LIMITS.default,
): {width: number; bodyHeight: number} {
  const safeAspect = aspect > 0 ? aspect : 16 / 10;
  let width = clamp(seed.width, PIP_SIZE_LIMITS.minWidth, PIP_SIZE_LIMITS.maxWidth);
  let bodyHeight = width / safeAspect;

  if (bodyHeight > PIP_SIZE_LIMITS.maxBodyHeight) {
    bodyHeight = PIP_SIZE_LIMITS.maxBodyHeight;
    width = clamp(
      bodyHeight * safeAspect,
      PIP_SIZE_LIMITS.minWidth,
      PIP_SIZE_LIMITS.maxWidth,
    );
  }
  if (bodyHeight < PIP_SIZE_LIMITS.minBodyHeight) {
    bodyHeight = PIP_SIZE_LIMITS.minBodyHeight;
    width = clamp(
      bodyHeight * safeAspect,
      PIP_SIZE_LIMITS.minWidth,
      PIP_SIZE_LIMITS.maxWidth,
    );
  }

  return {
    width: Math.round(width),
    bodyHeight: Math.round(bodyHeight),
  };
}

export function pipSizeFromWidth(
  width: number,
  aspect: number,
): {width: number; bodyHeight: number} {
  return pipSizeForAspect(aspect, {
    width,
    bodyHeight: width / (aspect > 0 ? aspect : 16 / 10),
  });
}

export function pipSizeFromBodyHeight(
  bodyHeight: number,
  aspect: number,
): {width: number; bodyHeight: number} {
  const safeAspect = aspect > 0 ? aspect : 16 / 10;
  return pipSizeForAspect(aspect, {
    width: bodyHeight * safeAspect,
    bodyHeight,
  });
}
