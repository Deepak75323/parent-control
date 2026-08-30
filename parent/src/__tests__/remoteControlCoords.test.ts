import {
  cameraFacingFromStream,
  cameraPreviewFrameSize,
  containVideoLayoutBox,
  coverVideoLayoutBox,
  displayVideoAspectFromFrameSize,
  mapContainTouchToNorm,
  resolveCameraObjectFit,
  videoAspectFromStream,
  viewerVideoAspect,
} from '../components/liveMonitoring/remoteControlCoords';

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

  it('uses portrait display aspect for landscape mobile camera buffers', () => {
    expect(displayVideoAspectFromFrameSize(1280, 960, true)).toBeCloseTo(960 / 1280);
    expect(displayVideoAspectFromFrameSize(1280, 960, false)).toBeCloseTo(1280 / 960);
  });

  it('letterboxes 4:3 camera in a portrait viewport', () => {
    const box = containVideoLayoutBox(400, 800, 3 / 4);
    expect(box.width).toBeCloseTo(400);
    expect(box.height).toBeCloseTo(533.33, 1);
    expect(box.left).toBe(0);
    expect(box.top).toBeCloseTo(133.33, 1);
  });

  it('cover-fills a portrait viewport for 16:9 camera', () => {
    const box = coverVideoLayoutBox(400, 800, 9 / 16);
    expect(box.width).toBeCloseTo(450);
    expect(box.height).toBeCloseTo(800);
    expect(box.left).toBeCloseTo(-25);
    expect(box.top).toBe(0);
  });

  it('infers camera aspect from stream settings when frame size is unknown', () => {
    const stream = {
      getVideoTracks: () => [
        {
          getSettings: () => ({width: 1280, height: 960}),
        },
      ],
    } as unknown as import('react-native-webrtc').MediaStream;
    expect(viewerVideoAspect(stream, 'CAMERA', null)).toBeCloseTo(960 / 1280);
  });

  it('reads facingMode from track settings', () => {
    const front = {
      getVideoTracks: () => [{getSettings: () => ({facingMode: 'user'})}],
    } as unknown as import('react-native-webrtc').MediaStream;
    expect(cameraFacingFromStream(front)).toBe('user');
  });

  it('returns stable 3:4 camera frame size for portrait viewport', () => {
    const frame = cameraPreviewFrameSize(400, 800, 44, 56, false);
    expect(frame.width / frame.height).toBeCloseTo(0.75, 2);
    expect(frame.height).toBeLessThanOrEqual(800 - 44 - 56 - 40);
  });

  it('uses fullscreen viewport when fullscreen', () => {
    expect(cameraPreviewFrameSize(400, 800, 44, 56, true)).toEqual({
      width: 400,
      height: 800,
    });
  });
});

describe('resolveCameraObjectFit', () => {
  it('always uses cover for Discord-style remote camera', () => {
    expect(resolveCameraObjectFit('user', 1)).toBe('cover');
    expect(resolveCameraObjectFit('environment', 1)).toBe('cover');
    expect(resolveCameraObjectFit('user', 1.5)).toBe('cover');
    expect(resolveCameraObjectFit('environment', 2)).toBe('cover');
  });
});
