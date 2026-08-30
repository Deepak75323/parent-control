import {describe, expect, it} from 'vitest';
import {webCameraCssRotation} from './cameraStreamUpright';

describe('webCameraCssRotation', () => {
  it('applies 270 when coded buffer is landscape and CVO is 270', () => {
    expect(webCameraCssRotation(1920, 1440, 270)).toBe(270);
  });

  it('does not use 90 for landscape buffers', () => {
    expect(webCameraCssRotation(1920, 1440, 0)).toBe(270);
    expect(webCameraCssRotation(1920, 1440, 270)).not.toBe(90);
  });

  it('skips CSS rotation when browser reports portrait (CVO already applied)', () => {
    expect(webCameraCssRotation(1080, 1440, 270)).toBe(0);
  });

  it('returns 0 for upright portrait with no CVO', () => {
    expect(webCameraCssRotation(1080, 1440, 0)).toBe(0);
  });

  it('applies 180 CVO', () => {
    expect(webCameraCssRotation(1920, 1440, 180)).toBe(180);
  });
});
