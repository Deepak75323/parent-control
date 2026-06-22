import {mapContainTouchToNorm, videoAspectFromStream} from '../components/liveMonitoring/remoteControlCoords';

describe('remoteControlCoords', () => {
  it('maps center tap to 0.5, 0.5 for 16:9 video in portrait viewport', () => {
    const viewportW = 400;
    const viewportH = 800;
    const aspect = 9 / 16;
    const renderH = viewportH;
    const renderW = viewportH * aspect;
    const offsetX = (viewportW - renderW) / 2;
    const result = mapContainTouchToNorm(
      offsetX + renderW / 2,
      renderH / 2,
      viewportW,
      viewportH,
      aspect,
    );
    expect(result).toEqual({xNorm: 0.5, yNorm: 0.5});
  });

  it('returns null for taps outside letterbox', () => {
    const result = mapContainTouchToNorm(0, 0, 400, 800, 9 / 16);
    expect(result).toBeNull();
  });

  it('falls back to 9/16 when stream has no video track', () => {
    expect(videoAspectFromStream(null)).toBeCloseTo(9 / 16);
  });
});
