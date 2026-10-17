import {describe, expect, it} from 'vitest';
import {
  clampViewerPinchScale,
  formatViewerZoomLabel,
  nextPinchScale,
  nextViewerRotateDeg,
  nudgeViewerZoomPercent,
  prevViewerRotateDeg,
  viewerContainerAspect,
  viewerVideoRotateStyle,
  viewerZoomLayerStyle,
  clampViewerPan,
  VIEWER_ZOOM_STEP_PERCENT,
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

  it('clamps pinch zoom and applies scale to video transform', () => {
    expect(clampViewerPinchScale(0.2)).toBe(1);
    expect(clampViewerPinchScale(9)).toBe(3);
    expect(nextPinchScale(1, 100, 200)).toBeCloseTo(1.4);
    const style = viewerVideoRotateStyle(0, 640, 400, 'contain', 2);
    expect(style.transform).toBe('scale(2)');
  });

  it('nudges zoom with 5% steps and formats labels', () => {
    expect(formatViewerZoomLabel(1)).toBe('100%');
    expect(formatViewerZoomLabel(1.5)).toBe('150%');
    expect(nudgeViewerZoomPercent(1, VIEWER_ZOOM_STEP_PERCENT)).toBe(1.05);
    expect(nudgeViewerZoomPercent(1.05, -VIEWER_ZOOM_STEP_PERCENT)).toBe(1);
  });

  it('scales camera canvas layer via CSS', () => {
    expect(viewerZoomLayerStyle(1).transform).toBe('none');
    expect(viewerZoomLayerStyle(2).transform).toBe('scale(2)');
  });

  it('clamps pan so a zoomed picture stays inside the frame', () => {
    expect(clampViewerPan({x: 400, y: -400}, 2, 200, 100)).toEqual({x: 100, y: -50});
    expect(clampViewerPan({x: 40, y: 10}, 1, 200, 100)).toEqual({x: 0, y: 0});
  });

  it('shifts the zoomed layer when the picture is panned', () => {
    expect(viewerZoomLayerStyle(2, {x: 12, y: -8}).transform).toBe(
      'translate(12px, -8px) scale(2)',
    );
  });
});
