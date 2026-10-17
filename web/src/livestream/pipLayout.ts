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

export const MOBILE_PIP_BREAKPOINT = 760;

export type PipSizeLimits = {
  minWidth: number;
  maxWidth: number;
  minBodyHeight: number;
  maxBodyHeight: number;
  default: {width: number; bodyHeight: number};
};

export function isMobilePipViewport(): boolean {
  return typeof window !== 'undefined' && window.innerWidth <= MOBILE_PIP_BREAKPOINT;
}

/** Tighter PiP on phones so chat and other pages stay usable. */
export function getPipSizeLimits(): PipSizeLimits {
  if (!isMobilePipViewport()) {
    return PIP_SIZE_LIMITS;
  }
  const maxWidth = Math.min(280, Math.max(200, window.innerWidth - 24));
  return {
    minWidth: 200,
    maxWidth,
    minBodyHeight: 72,
    maxBodyHeight: 150,
    default: {
      width: Math.min(240, maxWidth),
      bodyHeight: 120,
    },
  };
}

export function largePipSize() {
  const limits = getPipSizeLimits();
  if (typeof window === 'undefined') {
    return {width: 560, bodyHeight: 420};
  }
  if (isMobilePipViewport()) {
    return pipSizeForAspect(16 / 10, {
      width: limits.maxWidth,
      bodyHeight: limits.maxBodyHeight,
    });
  }
  return {
    width: Math.min(limits.maxWidth, window.innerWidth - 32),
    bodyHeight: Math.min(
      limits.maxBodyHeight,
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

export function pipSizeForAspect(
  aspect: number,
  seed: {width: number; bodyHeight: number} = getPipSizeLimits().default,
): {width: number; bodyHeight: number} {
  const limits = getPipSizeLimits();
  const safeAspect = aspect > 0 ? aspect : 16 / 10;
  let width = clamp(seed.width, limits.minWidth, limits.maxWidth);
  let bodyHeight = width / safeAspect;

  if (bodyHeight > limits.maxBodyHeight) {
    bodyHeight = limits.maxBodyHeight;
    width = clamp(
      bodyHeight * safeAspect,
      limits.minWidth,
      limits.maxWidth,
    );
  }
  if (bodyHeight < limits.minBodyHeight) {
    bodyHeight = limits.minBodyHeight;
    width = clamp(
      bodyHeight * safeAspect,
      limits.minWidth,
      limits.maxWidth,
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
