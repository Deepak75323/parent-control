/** Clockwise viewer-only rotation for the web live video (CSS; does not move the child camera). */
export type ViewerRotateDeg = 0 | 90 | 180 | 270;

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
export function viewerVideoRotateStyle(
  degrees: ViewerRotateDeg,
  containerW: number,
  containerH: number,
  objectFit: 'contain' | 'cover' = 'contain',
): ViewerVideoStyle {
  if (degrees === 0) {
    return {
      transform: 'none',
      width: '100%',
      height: '100%',
      position: 'absolute',
      inset: 0,
      objectFit,
    };
  }
  if (degrees === 180) {
    return {
      transform: 'rotate(180deg)',
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
    transform: `rotate(${degrees}deg)`,
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
