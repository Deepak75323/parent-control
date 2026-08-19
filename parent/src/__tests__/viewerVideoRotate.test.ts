import {
  clampViewerPinchScale,
  nextPinchScale,
  nextViewerRotateDeg,
  prevViewerRotateDeg,
  rnViewerVideoRotateStyle,
  viewerRotateFitScale,
} from '../utils/viewerVideoRotate';

describe('viewerVideoRotate (parent)', () => {
  it('cycles clockwise and counter-clockwise', () => {
    expect(nextViewerRotateDeg(0)).toBe(90);
    expect(nextViewerRotateDeg(270)).toBe(0);
    expect(prevViewerRotateDeg(0)).toBe(270);
    expect(prevViewerRotateDeg(90)).toBe(0);
  });

  it('letterboxes 90° instead of enlarging the frame', () => {
    const style = rnViewerVideoRotateStyle(90, 360, 700);
    expect(style.width).toBeUndefined();
    expect(style.height).toBeUndefined();
    expect(style.left).toBe(0);
    expect(style.top).toBe(0);
    expect(style.transform).toEqual([{rotate: '90deg'}, {scale: viewerRotateFitScale(90, 360, 700)}]);
    expect(viewerRotateFitScale(90, 360, 700)).toBeCloseTo(360 / 700);
  });

  it('keeps fill for 0° and only rotates 180°', () => {
    expect(rnViewerVideoRotateStyle(0, 800, 450).transform).toBeUndefined();
    expect(rnViewerVideoRotateStyle(180, 800, 450).transform).toEqual([{rotate: '180deg'}]);
  });

  it('clamps pinch zoom and multiplies it after rotate-fit', () => {
    expect(clampViewerPinchScale(0.2)).toBe(1);
    expect(clampViewerPinchScale(9)).toBe(4);
    expect(nextPinchScale(1, 100, 200)).toBe(2);
    const style = rnViewerVideoRotateStyle(0, 360, 700, 2);
    expect(style.transform).toEqual([{scale: 2}]);
  });
});
