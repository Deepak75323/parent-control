/** Clockwise viewer-only rotation for the web live video (CSS; does not move the child camera). */
export type ViewerRotateDeg = 0 | 90 | 180 | 270;

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
  objectFit: 'contain';
};

export function nextViewerRotateDeg(current: ViewerRotateDeg): ViewerRotateDeg {
  return ((current + 90) % 360) as ViewerRotateDeg;
}

export function prevViewerRotateDeg(current: ViewerRotateDeg): ViewerRotateDeg {
  return ((current + 270) % 360) as ViewerRotateDeg;
}

/**
 * Layout style so a CSS-rotated &lt;video&gt; still fits the container without clipping.
 * For 90°/270°, width/height are swapped before rotate so the post-rotate box matches the wrap.
 */
export function viewerVideoRotateStyle(
  degrees: ViewerRotateDeg,
  containerW: number,
  containerH: number,
): ViewerVideoStyle {
  if (degrees === 0) {
    return {
      transform: 'none',
      width: '100%',
      height: '100%',
      position: 'absolute',
      inset: 0,
      objectFit: 'contain',
    };
  }
  if (degrees === 180) {
    return {
      transform: 'rotate(180deg)',
      width: '100%',
      height: '100%',
      position: 'absolute',
      inset: 0,
      objectFit: 'contain',
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
    objectFit: 'contain',
  };
}
