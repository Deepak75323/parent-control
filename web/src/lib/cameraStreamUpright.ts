import type {ViewerRotateDeg} from './viewerVideoRotate';
import {createMediaStreamTrackProcessor} from './mediaStreamTrackProcessor';

/**
 * Chrome's <video> often ignores WebRTC CVO on remote camera tracks.
 * Re-apply the sender's rotation via CSS when the element still reports coded buffer size.
 */
export function webCameraCssRotation(
  videoWidth: number,
  videoHeight: number,
  cvoRotation: number,
): ViewerRotateDeg {
  const cvo = normalizeCvo(cvoRotation);
  if (cvo === 0) {
    return videoWidth > videoHeight ? 270 : 0;
  }
  const codedLandscape = videoWidth > videoHeight;
  if (codedLandscape) {
    return cvo;
  }
  // Portrait dimensions — browser already applied CVO on the <video> element.
  return 0;
}

function normalizeCvo(degrees: number): ViewerRotateDeg {
  const n = ((Math.round(degrees) % 360) + 360) % 360;
  if (n === 0 || n === 90 || n === 180 || n === 270) {
    return n as ViewerRotateDeg;
  }
  return 0;
}

/** Read CVO from the first WebCodecs VideoFrame (when supported). */
export async function readVideoTrackCvo(track: MediaStreamTrack): Promise<number> {
  const processor = createMediaStreamTrackProcessor(track);
  if (!processor) {
    return 270;
  }
  const reader = processor.readable.getReader();
  try {
    const {value: frame} = await reader.read();
    const rotation = frame?.rotation ?? 0;
    frame?.close();
    return rotation;
  } catch {
    return 270;
  } finally {
    try {
      await reader.cancel();
    } catch {
      /* ignore */
    }
  }
}

/** @deprecated use webCameraCssRotation */
export function cameraStreamUprightCorrection(
  videoWidth: number,
  videoHeight: number,
): ViewerRotateDeg {
  return webCameraCssRotation(videoWidth, videoHeight, 0);
}
