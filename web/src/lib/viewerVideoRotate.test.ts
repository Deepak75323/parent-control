import {describe, expect, it} from 'vitest';
import {
  nextViewerRotateDeg,
  prevViewerRotateDeg,
  viewerVideoRotateStyle,
} from './viewerVideoRotate';

describe('viewerVideoRotate', () => {
  it('cycles clockwise and counter-clockwise', () => {
    expect(nextViewerRotateDeg(0)).toBe(90);
    expect(nextViewerRotateDeg(270)).toBe(0);
    expect(prevViewerRotateDeg(0)).toBe(270);
    expect(prevViewerRotateDeg(90)).toBe(0);
  });

  it('swaps box size for 90° so rotated video fills 800×450 wrap', () => {
    const style = viewerVideoRotateStyle(90, 800, 450);
    expect(style.transform).toBe('rotate(90deg)');
    expect(style.width).toBe(450);
    expect(style.height).toBe(800);
    expect(style.marginLeft).toBe(-225);
    expect(style.marginTop).toBe(-400);
  });

  it('keeps full-bleed box for 0° and 180°', () => {
    expect(viewerVideoRotateStyle(0, 800, 450).transform).toBe('none');
    expect(viewerVideoRotateStyle(180, 800, 450).transform).toBe('rotate(180deg)');
  });
});
