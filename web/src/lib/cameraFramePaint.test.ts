import {describe, expect, it, vi} from 'vitest';
import {
  browserReportsRotatedSize,
  displayRotationDegrees,
  drawnSourceSize,
  frameCvoDegrees,
  inferStreamFacingFromCvo,
  paintVideoFrameToCanvas,
  resolveFrontCameraForPaint,
  totalCanvasRotation,
  videoFrameDisplaySize,
} from './cameraFramePaint';

/**
 * Real capture from the Xiaomi child device in Chrome: portrait phone, front camera.
 * Confirmed on-device that 270° of canvas rotation renders this upright.
 */
const chromePortraitFrame = {
  codedWidth: 640,
  codedHeight: 480,
  displayWidth: 480,
  displayHeight: 640,
  rotation: 270,
};

describe('browserReportsRotatedSize', () => {
  it('detects Chrome reporting the upright display size', () => {
    expect(browserReportsRotatedSize(chromePortraitFrame)).toBe(true);
  });

  it('is false when display size still matches the coded landscape buffer', () => {
    expect(
      browserReportsRotatedSize({
        codedWidth: 640,
        codedHeight: 480,
        displayWidth: 640,
        displayHeight: 480,
        rotation: 270,
      }),
    ).toBe(false);
  });
});

describe('drawnSourceSize', () => {
  it('uses the reported display size as the natural draw size', () => {
    expect(drawnSourceSize(chromePortraitFrame)).toEqual({width: 480, height: 640});
  });

  it('falls back to coded pixels when display size matches the buffer', () => {
    expect(
      drawnSourceSize({
        codedWidth: 640,
        codedHeight: 480,
        displayWidth: 640,
        displayHeight: 480,
        rotation: 270,
      }),
    ).toEqual({width: 640, height: 480});
  });
});

describe('videoFrameDisplaySize', () => {
  it('uses displayWidth when the browser provides it', () => {
    expect(
      videoFrameDisplaySize({
        codedWidth: 1440,
        codedHeight: 1080,
        displayWidth: 1080,
        displayHeight: 1440,
        rotation: 270,
      }),
    ).toEqual({width: 1080, height: 1440});
  });

  it('derives portrait size from coded dimensions and rotation', () => {
    expect(
      videoFrameDisplaySize({
        codedWidth: 1440,
        codedHeight: 1080,
        displayWidth: 0,
        displayHeight: 0,
        rotation: 270,
      }),
    ).toEqual({width: 1080, height: 1440});
  });

  it('ignores displayWidth when it still matches coded landscape under 90/270 CVO', () => {
    expect(
      videoFrameDisplaySize({
        codedWidth: 1920,
        codedHeight: 1440,
        displayWidth: 1920,
        displayHeight: 1440,
        rotation: 270,
      }),
    ).toEqual({width: 1440, height: 1920});
  });
});

describe('frameCvoDegrees', () => {
  it('uses rotation metadata when the browser leaves the buffer unrotated', () => {
    expect(
      frameCvoDegrees({
        codedWidth: 1440,
        codedHeight: 1080,
        displayWidth: 0,
        displayHeight: 0,
        rotation: 270,
      }),
    ).toBe(270);
  });

  it('still applies CVO when the browser only reports the rotated size', () => {
    expect(frameCvoDegrees(chromePortraitFrame)).toBe(270);
  });

  it('assumes no rotation when the browser already delivered upright portrait pixels', () => {
    expect(
      frameCvoDegrees({
        codedWidth: 1080,
        codedHeight: 1440,
        displayWidth: 1080,
        displayHeight: 1440,
      }),
    ).toBe(0);
  });

  it('honours explicit CVO 0 on a landscape buffer (landscape phone tilt)', () => {
    expect(
      frameCvoDegrees({
        codedWidth: 640,
        codedHeight: 480,
        displayWidth: 640,
        displayHeight: 480,
        rotation: 0,
      }),
    ).toBe(0);
  });
});

describe('inferStreamFacingFromCvo', () => {
  it('maps portrait front camera CVO 270 to user', () => {
    expect(inferStreamFacingFromCvo(270, 640, 480)).toBe('user');
  });

  it('maps portrait back camera CVO 90 to environment', () => {
    expect(inferStreamFacingFromCvo(90, 640, 480)).toBe('environment');
  });

  it('maps CVO 270 on portrait-coded buffers (browser already swapped size)', () => {
    expect(inferStreamFacingFromCvo(270, 480, 640)).toBe('user');
  });

  it('maps CVO 90 on portrait-coded buffers', () => {
    expect(inferStreamFacingFromCvo(90, 480, 640)).toBe('environment');
  });

  it('returns null for ambiguous landscape tilt', () => {
    expect(inferStreamFacingFromCvo(0, 640, 480)).toBeNull();
  });
});

describe('resolveFrontCameraForPaint', () => {
  const frontPortrait = {
    codedWidth: 640,
    codedHeight: 480,
    displayWidth: 480,
    displayHeight: 640,
    rotation: 270,
  };

  it('prefers CVO inference over a stale hint after flip', () => {
    expect(resolveFrontCameraForPaint(frontPortrait, 'environment')).toBe(true);
  });

  it('keeps front paint when Flip toggles hint before CVO updates on portrait buffers', () => {
    const portraitCodedFront = {
      codedWidth: 480,
      codedHeight: 640,
      displayWidth: 480,
      displayHeight: 640,
      rotation: 270,
    };
    expect(resolveFrontCameraForPaint(portraitCodedFront, 'environment')).toBe(true);
    expect(totalCanvasRotation(portraitCodedFront, 0, true)).toBe(90);
    expect(
      totalCanvasRotation(
        portraitCodedFront,
        0,
        resolveFrontCameraForPaint(portraitCodedFront, 'environment'),
      ),
    ).toBe(90);
  });

  it('falls back to hint when CVO is ambiguous', () => {
    expect(
      resolveFrontCameraForPaint(
        {codedWidth: 640, codedHeight: 480, displayWidth: 640, displayHeight: 480, rotation: 0},
        'environment',
      ),
    ).toBe(false);
  });
});

describe('displayRotationDegrees', () => {
  it('compensates the mirrored preview for a front lens in portrait', () => {
    expect(
      displayRotationDegrees(
        {codedWidth: 640, codedHeight: 480, displayWidth: 480, displayHeight: 640, rotation: 270},
        true,
      ),
    ).toBe(90);
  });

  it('does not rotate CVO 0 on a portrait-sized buffer', () => {
    expect(
      displayRotationDegrees(
        {codedWidth: 1080, codedHeight: 1440, displayWidth: 1080, displayHeight: 1440, rotation: 0},
        true,
      ),
    ).toBe(0);
  });

  it('maps landscape-right CVO 0 to 90 degrees (front mirror + landscape pixels)', () => {
    expect(
      displayRotationDegrees(
        {codedWidth: 640, codedHeight: 480, displayWidth: 640, displayHeight: 480, rotation: 0},
        true,
      ),
    ).toBe(90);
  });

  it('maps landscape-left CVO 180 to 270 degrees', () => {
    expect(
      displayRotationDegrees(
        {codedWidth: 640, codedHeight: 480, displayWidth: 480, displayHeight: 640, rotation: 180},
        true,
      ),
    ).toBe(270);
  });

  it('uses CVO unchanged for a back lens', () => {
    expect(
      displayRotationDegrees(
        {codedWidth: 640, codedHeight: 480, displayWidth: 640, displayHeight: 480, rotation: 90},
        false,
      ),
    ).toBe(90);
  });
});

describe('totalCanvasRotation', () => {
  const landscapeCvo270 = {
    codedWidth: 1440,
    codedHeight: 1080,
    displayWidth: 0,
    displayHeight: 0,
    rotation: 270,
  };

  it('renders the front-camera portrait stream upright with no viewer rotation', () => {
    expect(totalCanvasRotation(landscapeCvo270, 0, true)).toBe(90);
  });

  it('applies CVO directly for the back camera', () => {
    expect(totalCanvasRotation(landscapeCvo270, 0, false)).toBe(270);
  });

  it('adds manual viewer rotation on top', () => {
    expect(totalCanvasRotation(landscapeCvo270, 90, true)).toBe(180);
    expect(totalCanvasRotation(landscapeCvo270, 270, true)).toBe(0);
  });

  it('renders front-camera landscape-right (CVO 0) upright', () => {
    expect(
      totalCanvasRotation(
        {codedWidth: 640, codedHeight: 480, displayWidth: 640, displayHeight: 480, rotation: 0},
        0,
        true,
      ),
    ).toBe(90);
  });

  it('renders front-camera landscape-left (CVO 180) upright', () => {
    expect(
      totalCanvasRotation(
        {codedWidth: 640, codedHeight: 480, displayWidth: 480, displayHeight: 640, rotation: 180},
        0,
        true,
      ),
    ).toBe(270);
  });

  it('does not rotate upright portrait frames', () => {
    expect(
      totalCanvasRotation({
        codedWidth: 1080,
        codedHeight: 1440,
        displayWidth: 1080,
        displayHeight: 1440,
      }),
    ).toBe(0);
  });
});

describe('paintVideoFrameToCanvas', () => {
  function fakeCtx() {
    return {
      fillStyle: '',
      fillRect: vi.fn(),
      save: vi.fn(),
      restore: vi.fn(),
      translate: vi.fn(),
      rotate: vi.fn(),
      drawImage: vi.fn(),
    } as unknown as CanvasRenderingContext2D;
  }

  it('rotates coded pixels by the sender CVO for a back-camera stream', () => {
    const ctx = fakeCtx();
    paintVideoFrameToCanvas(
      ctx,
      {codedWidth: 1440, codedHeight: 1080, displayWidth: 0, displayHeight: 0, rotation: 270},
      400,
      700,
      'cover',
      0,
      false,
    );

    expect(ctx.rotate).toHaveBeenCalledWith((270 * Math.PI) / 180);
  });

  it('renders the on-device front-camera capture upright with no manual rotation', () => {
    const ctx = fakeCtx();
    paintVideoFrameToCanvas(ctx, chromePortraitFrame, 400, 700, 'cover', 0, true);

    expect(ctx.rotate).toHaveBeenCalledWith((90 * Math.PI) / 180);
  });

  it('draws the Chrome capture at its reported natural size', () => {
    const ctx = fakeCtx();
    paintVideoFrameToCanvas(ctx, chromePortraitFrame, 640, 480, 'contain', 0, true);

    // Rotated 90°, the 480x640 source occupies a 640x480 footprint — scale 1.
    expect(ctx.drawImage).toHaveBeenCalledWith(expect.anything(), -240, -320, 480, 640);
  });

  it('adds manual viewer rotation on top of the upright correction', () => {
    const ctx = fakeCtx();
    paintVideoFrameToCanvas(ctx, chromePortraitFrame, 400, 700, 'cover', 180, true);

    expect(ctx.rotate).toHaveBeenCalledWith((270 * Math.PI) / 180);
  });

  it('cancels rotation when manual rotate brings the total back to zero', () => {
    const ctx = fakeCtx();
    paintVideoFrameToCanvas(ctx, chromePortraitFrame, 400, 700, 'cover', 270, true);

    expect(ctx.rotate).not.toHaveBeenCalled();
  });

  it('draws the coded footprint, not a squashed upright box', () => {
    const ctx = fakeCtx();
    paintVideoFrameToCanvas(
      ctx,
      {codedWidth: 1440, codedHeight: 1080, displayWidth: 0, displayHeight: 0, rotation: 270},
      1080,
      1440,
      'contain',
      0,
      false,
    );

    // Rotated footprint is 1080x1440, so scale = 1; drawImage keeps the coded aspect.
    expect(ctx.drawImage).toHaveBeenCalledWith(expect.anything(), -720, -540, 1440, 1080);
  });

  it('combines viewer rotation with CVO', () => {
    const ctx = fakeCtx();
    paintVideoFrameToCanvas(
      ctx,
      {codedWidth: 1440, codedHeight: 1080, displayWidth: 0, displayHeight: 0, rotation: 270},
      400,
      700,
      'cover',
      180,
      false,
    );

    expect(ctx.rotate).toHaveBeenCalledWith((90 * Math.PI) / 180);
  });

  it('skips rotation entirely for upright portrait frames', () => {
    const ctx = fakeCtx();
    paintVideoFrameToCanvas(
      ctx,
      {codedWidth: 1080, codedHeight: 1440, displayWidth: 1080, displayHeight: 1440},
      400,
      700,
      'cover',
      0,
    );

    expect(ctx.rotate).not.toHaveBeenCalled();
  });
});
