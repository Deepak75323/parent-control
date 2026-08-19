import {
  clampViewerPinchScale,
  nextPinchScale,
  nextViewerRotateDeg,
  nudgePinchScale,
  nudgeViewerZoomPercent,
  prevViewerRotateDeg,
  rnViewerVideoRotateStyle,
  androidViewerVideoLayout,
  viewerRotateFitScale,
  formatViewerZoomLabel,
  scaleToViewerZoomPercent,
  viewerZoomPercentToScale,
  VIEWER_ZOOM_STEP_PERCENT,
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

  it('nudges zoom with buttons and clamps at limits', () => {
    expect(nudgePinchScale(1, 1.25)).toBe(1.25);
    expect(nudgePinchScale(1.25, 0.8)).toBe(1);
    expect(nudgePinchScale(4, 1.25)).toBe(4);
    expect(nudgePinchScale(1, 0.5)).toBe(1);
  });

  it('uses percentage zoom labels and 10% steps', () => {
    expect(formatViewerZoomLabel(1)).toBe('100%');
    expect(formatViewerZoomLabel(1.5)).toBe('150%');
    expect(scaleToViewerZoomPercent(4)).toBe(400);
    expect(nudgeViewerZoomPercent(1, VIEWER_ZOOM_STEP_PERCENT)).toBe(1.1);
    expect(nudgeViewerZoomPercent(1.1, -VIEWER_ZOOM_STEP_PERCENT)).toBe(1);
    expect(viewerZoomPercentToScale(250)).toBe(2.5);
    expect(viewerZoomPercentToScale(450)).toBe(4);
  });

  it('sizes Android layout box for parent-side zoom', () => {
    const zoomed = androidViewerVideoLayout(0, 400, 800, 2);
    expect(zoomed.width).toBe(800);
    expect(zoomed.height).toBe(1600);
    expect(zoomed.left).toBe(-200);

    const rotated = androidViewerVideoLayout(90, 400, 800, 1);
    expect(rotated.width).toBe(400);
    expect(rotated.height).toBe(800);
    expect(rotated.left).toBe(0);
  });
});
