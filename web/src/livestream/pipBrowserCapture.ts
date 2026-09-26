/**
 * Chrome opens picture-in-picture on a tab change for pages that are capturing
 * a microphone or camera. The child camera is silent, so a local mic capture
 * stays on while the live view is open. The track is never played or sent.
 */
let capture: MediaStream | null = null;
let starting: Promise<boolean> | null = null;

export function isBrowserCaptureActive(): boolean {
  return Boolean(capture?.getTracks().some(track => track.readyState === 'live'));
}

export async function startBrowserCaptureForPip(): Promise<boolean> {
  if (isBrowserCaptureActive()) {
    return true;
  }
  if (starting) {
    return starting;
  }
  if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
    return false;
  }
  starting = navigator.mediaDevices
    .getUserMedia({audio: true, video: false})
    .then(stream => {
      capture = stream;
      return true;
    })
    .catch(() => false)
    .finally(() => {
      starting = null;
    });
  return starting;
}

export function stopBrowserCaptureForPip(): void {
  capture?.getTracks().forEach(track => track.stop());
  capture = null;
}
