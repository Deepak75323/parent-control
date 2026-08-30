import {describe, expect, it} from 'vitest';
import {
  nextViewerRotateDeg,
  prevViewerRotateDeg,
  viewerContainerAspect,
  viewerVideoRotateStyle,
} from './viewerVideoRotate';

describe('viewerVideoRotate', () => {
  it('cycles clockwise and counter-clockwise', () => {
    expect(nextViewerRotateDeg(0)).toBe(90);
    expect(nextViewerRotateDeg(270)).toBe(0);
    expect(prevViewerRotateDeg(0)).toBe(270);
    expect(prevViewerRotateDeg(90)).toBe(0);
  });

  it('swaps aspect ratio when the video is rotated 90°', () => {
    expect(viewerContainerAspect(1280, 960, 90)).toBeCloseTo(960 / 1280);
    expect(viewerContainerAspect(1280, 960, 0)).toBeCloseTo(1280 / 960);
  });

  it('sizes 90° rotation by swapping container dimensions', () => {
    const style = viewerVideoRotateStyle(90, 800, 500);
    expect(style.width).toBe(500);
    expect(style.height).toBe(800);
    expect(style.transform).toBe('rotate(90deg)');
  });
});
