import type {WebCameraFacing} from './cameraViewerLayout';
import type {ViewerRotateDeg} from './viewerVideoRotate';

export type VideoFrameLike = {
  codedWidth: number;
  codedHeight: number;
  displayWidth: number;
  displayHeight: number;
  rotation?: number;
};

/** Upright display size after WebRTC/WebCodecs rotation metadata. */
export function videoFrameDisplaySize(frame: VideoFrameLike): {width: number; height: number} {
  const rotation = frame.rotation ?? 0;
  const codedW = frame.codedWidth;
  const codedH = frame.codedHeight;
  if (codedW <= 0 || codedH <= 0) {
    return {width: 0, height: 0};
  }
  const swapped = rotation === 90 || rotation === 270;
  const uprightW = swapped ? codedH : codedW;
  const uprightH = swapped ? codedW : codedH;

  // Some browsers leave displayWidth/Height at coded (landscape) even when rotation ≠ 0.
  if (frame.displayWidth > 0 && frame.displayHeight > 0) {
    const dw = frame.displayWidth;
    const dh = frame.displayHeight;
    if (dw === uprightW && dh === uprightH) {
      return {width: dw, height: dh};
    }
  }

  return {width: uprightW, height: uprightH};
}

/**
 * True when the browser reports the CVO-rotated size in displayWidth/Height.
 *
 * Chrome does this while still handing `drawImage` unrotated pixels: the frame is scaled
 * into the upright box but never turned. So this affects the source size we draw, not the
 * rotation we must apply.
 */
export function browserReportsRotatedSize(frame: VideoFrameLike): boolean {
  const rotation = normalizeDegrees(frame.rotation ?? 0);
  if (rotation !== 90 && rotation !== 270) {
    return false;
  }
  return frame.displayWidth === frame.codedHeight && frame.displayHeight === frame.codedWidth;
}

/**
 * CVO the canvas must apply. Browsers do not rotate pixels for `drawImage`, so the sender's
 * rotation is always ours to apply. When metadata is missing, a landscape buffer means the
 * phone's portrait rotation was dropped and we fall back.
 */
export function frameCvoDegrees(frame: VideoFrameLike, fallbackLandscapeCvo = 270): number {
  if (frame.rotation != null) {
    return normalizeDegrees(frame.rotation);
  }
  return frame.codedWidth > frame.codedHeight ? fallbackLandscapeCvo : 0;
}

/**
 * Clockwise rotation that renders the frame upright on an unmirrored viewer.
 *
 * Front-camera CVO includes mirrored-preview compensation from the sender.
 * Portrait (CVO 270) → 90°. Landscape CVO (0 or 180) uses landscape drawImage
 * pixels and needs an extra 90° CW on top of mirror compensation.
 *
 * CVO 0 on a portrait-sized buffer means upright pixels already (no canvas rotation).
 */
export function displayRotationDegrees(
  frame: VideoFrameLike,
  frontCamera: boolean,
  fallbackLandscapeCvo = 270,
): number {
  const cvo = frameCvoDegrees(frame, fallbackLandscapeCvo);
  if (!frontCamera) {
    return cvo;
  }
  if (cvo === 0 && frame.codedHeight >= frame.codedWidth) {
    return 0;
  }
  const mirrored = normalizeDegrees(360 - cvo);
  if (frame.rotation != null && (cvo === 0 || cvo === 180)) {
    return normalizeDegrees(mirrored + 90);
  }
  return mirrored;
}

/** Total clockwise rotation the canvas applies: upright correction plus manual viewer rotate. */
export function totalCanvasRotation(
  frame: VideoFrameLike,
  viewerRotate: ViewerRotateDeg = 0,
  frontCamera = true,
  fallbackLandscapeCvo = 270,
): number {
  return normalizeDegrees(
    displayRotationDegrees(frame, frontCamera, fallbackLandscapeCvo) + viewerRotate,
  );
}

function normalizeDegrees(degrees: number): number {
  return ((Math.round(degrees) % 360) + 360) % 360;
}

/**
 * Portrait-upright heuristic from child CameraOrientationHelper defaults
 * (front sensor 270° → CVO 270, back sensor 90° → CVO 90).
 *
 * Do not require a landscape coded buffer — some browsers already report the
 * upright portrait size while still carrying CVO. Ignoring that made Flip's
 * optimistic facing hint apply the wrong paint path (~180° jump).
 */
export function inferStreamFacingFromCvo(
  rotation: number | undefined,
  codedWidth: number,
  codedHeight: number,
): WebCameraFacing | null {
  if (rotation == null || codedWidth <= 0 || codedHeight <= 0) {
    return null;
  }
  const cvo = normalizeDegrees(rotation);
  if (cvo === 270) {
    return 'user';
  }
  if (cvo === 90) {
    return 'environment';
  }
  return null;
}

/**
 * Pick front vs back paint path from live frame CVO when confident, else UI hint.
 * Avoids wrong rotation when Flip toggles store state before the child switches lenses.
 */
export function resolveFrontCameraForPaint(
  frame: VideoFrameLike,
  facingHint: WebCameraFacing,
): boolean {
  const inferred = inferStreamFacingFromCvo(
    frame.rotation,
    frame.codedWidth,
    frame.codedHeight,
  );
  if (inferred != null) {
    return inferred === 'user';
  }
  return facingHint === 'user';
}

/** Natural size `drawImage` uses for this frame, before any canvas rotation. */
export function drawnSourceSize(frame: VideoFrameLike): {width: number; height: number} {
  if (browserReportsRotatedSize(frame)) {
    return {width: frame.displayWidth, height: frame.displayHeight};
  }
  return {width: frame.codedWidth, height: frame.codedHeight};
}

/**
 * Paint a WebCodecs VideoFrame onto canvas, applying only the rotation the browser did not.
 * The layout box uses the rotated footprint so nothing is clipped or squashed.
 */
export function paintVideoFrameToCanvas(
  ctx: CanvasRenderingContext2D,
  frame: VideoFrameLike,
  canvasW: number,
  canvasH: number,
  objectFit: 'cover' | 'contain',
  viewerRotate: ViewerRotateDeg = 0,
  frontCamera = true,
  fallbackLandscapeCvo = 270,
): void {
  if (frame.codedWidth <= 0 || frame.codedHeight <= 0 || canvasW <= 0 || canvasH <= 0) {
    return;
  }

  const {width: srcW, height: srcH} = drawnSourceSize(frame);
  const total = totalCanvasRotation(frame, viewerRotate, frontCamera, fallbackLandscapeCvo);
  const swapped = total === 90 || total === 270;
  const layoutW = swapped ? srcH : srcW;
  const layoutH = swapped ? srcW : srcH;

  const scale =
    objectFit === 'cover'
      ? Math.max(canvasW / layoutW, canvasH / layoutH)
      : Math.min(canvasW / layoutW, canvasH / layoutH);
  const drawW = srcW * scale;
  const drawH = srcH * scale;

  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, canvasW, canvasH);

  ctx.save();
  ctx.translate(canvasW / 2, canvasH / 2);
  if (total !== 0) {
    ctx.rotate((total * Math.PI) / 180);
  }
  ctx.drawImage(frame as CanvasImageSource, -drawW / 2, -drawH / 2, drawW, drawH);
  ctx.restore();
}
