import {describe, expect, it} from 'vitest';
import {
  toggleCameraFacing,
  webCameraDisplayAspect,
  webCameraObjectFit,
} from './cameraViewerLayout';

describe('cameraViewerLayout', () => {
  it('toggles camera facing', () => {
    expect(toggleCameraFacing('user')).toBe('environment');
    expect(toggleCameraFacing('environment')).toBe('user');
  });

  it('uses portrait display aspect for landscape buffers', () => {
    expect(webCameraDisplayAspect(1920, 1440)).toBeCloseTo(1440 / 1920);
    expect(webCameraDisplayAspect(1080, 1920)).toBeCloseTo(1080 / 1920);
  });

  it('always uses cover for Discord-style remote camera', () => {
    expect(webCameraObjectFit('user')).toBe('cover');
    expect(webCameraObjectFit('environment')).toBe('cover');
  });
});
