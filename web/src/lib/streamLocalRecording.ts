/** Record a live WebRTC stream in the browser and download it to the parent's device. */

export type RecordableStreamType = 'SCREEN' | 'CAMERA' | 'AUDIO';

export type LocalRecordingMime = {
  mimeType: string;
  extension: string;
};

type TrackEnabledSnapshot = {
  track: MediaStreamTrack;
  enabled: boolean;
};

let activeRecorder: MediaRecorder | null = null;
let activeChunks: Blob[] = [];
let activeMime: LocalRecordingMime | null = null;
let activeStreamType: RecordableStreamType | null = null;
let audioSnapshots: TrackEnabledSnapshot[] = [];
let includedAudio = false;

function extensionForMime(mimeType: string): string {
  if (mimeType.startsWith('audio/')) {
    return mimeType.includes('mp4') ? 'm4a' : 'webm';
  }
  return mimeType.includes('mp4') ? 'mp4' : 'webm';
}

/** Prefer codecs that keep both video and the child room mic in one file. */
export function pickLocalRecordingMime(
  streamType: RecordableStreamType,
  isTypeSupported: (mimeType: string) => boolean = mime =>
    typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(mime),
): LocalRecordingMime | null {
  const candidates =
    streamType === 'AUDIO'
      ? ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4']
      : [
          'video/webm;codecs=vp9,opus',
          'video/webm;codecs=vp8,opus',
          'video/webm;codecs=vp9',
          'video/webm;codecs=vp8',
          'video/webm',
          'video/mp4',
        ];
  for (const mimeType of candidates) {
    if (isTypeSupported(mimeType)) {
      return {mimeType, extension: extensionForMime(mimeType)};
    }
  }
  // Some browsers accept MediaRecorder without an explicit mimeType.
  return {mimeType: '', extension: streamType === 'AUDIO' ? 'webm' : 'webm'};
}

export function buildLocalRecordingFilename(
  streamType: RecordableStreamType,
  extension: string,
  now: Date = new Date(),
): string {
  const stamp = now.toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19);
  const kind = streamType.toLowerCase();
  return `parentcontrol-${kind}-${stamp}.${extension}`;
}

export function isLocalStreamRecordingSupported(): boolean {
  return typeof MediaRecorder !== 'undefined';
}

export function isLocalStreamRecordingActive(): boolean {
  return activeRecorder != null && activeRecorder.state !== 'inactive';
}

function restoreAudioTracks(): void {
  for (const snap of audioSnapshots) {
    try {
      snap.track.enabled = snap.enabled;
    } catch {
      /* track may already be ended */
    }
  }
  audioSnapshots = [];
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

/**
 * Start capturing the live remote stream. Audio tracks are temporarily enabled so the
 * download includes the child room mic even when the parent has speakers muted.
 */
export function startLocalStreamRecording(
  stream: MediaStream,
  streamType: RecordableStreamType,
): void {
  if (!isLocalStreamRecordingSupported()) {
    throw new Error('This browser cannot record live video.');
  }
  if (isLocalStreamRecordingActive()) {
    throw new Error('A recording is already in progress.');
  }
  const hasVideo = stream.getVideoTracks().some(t => t.readyState === 'live');
  const hasAudio = stream.getAudioTracks().some(t => t.readyState === 'live');
  if (streamType === 'AUDIO' && !hasAudio) {
    throw new Error('Wait for live audio before recording.');
  }
  if (streamType !== 'AUDIO' && !hasVideo) {
    throw new Error('Wait for live video before recording.');
  }

  const mime = pickLocalRecordingMime(streamType);
  if (!mime) {
    throw new Error('This browser cannot record live video.');
  }

  audioSnapshots = stream.getAudioTracks().map(track => ({track, enabled: track.enabled}));
  for (const track of stream.getAudioTracks()) {
    if (track.readyState === 'live') {
      track.enabled = true;
    }
  }
  includedAudio = stream.getAudioTracks().some(t => t.readyState === 'live' && t.enabled);

  const recorder = mime.mimeType
    ? new MediaRecorder(stream, {mimeType: mime.mimeType, videoBitsPerSecond: 2_500_000})
    : new MediaRecorder(stream, {videoBitsPerSecond: 2_500_000});
  activeChunks = [];
  activeMime = mime;
  activeStreamType = streamType;
  recorder.ondataavailable = event => {
    if (event.data.size > 0) {
      activeChunks.push(event.data);
    }
  };
  recorder.start(1000);
  activeRecorder = recorder;
}

export async function stopLocalStreamRecordingAndDownload(): Promise<{
  filename: string;
  includedAudio: boolean;
}> {
  const recorder = activeRecorder;
  const mime = activeMime;
  const streamType = activeStreamType;
  const hadAudio = includedAudio;
  if (!recorder || !mime || !streamType) {
    restoreAudioTracks();
    activeRecorder = null;
    throw new Error('No active recording.');
  }

  const blob = await new Promise<Blob>((resolve, reject) => {
    const finish = () => {
      const type = mime.mimeType || (streamType === 'AUDIO' ? 'audio/webm' : 'video/webm');
      resolve(new Blob(activeChunks, {type}));
    };
    recorder.onerror = () => reject(new Error('Recording failed.'));
    if (recorder.state === 'inactive') {
      finish();
      return;
    }
    recorder.onstop = finish;
    try {
      recorder.stop();
    } catch (err) {
      reject(err instanceof Error ? err : new Error('Could not stop recording.'));
    }
  });

  restoreAudioTracks();
  activeRecorder = null;
  activeChunks = [];
  activeMime = null;
  activeStreamType = null;
  includedAudio = false;

  if (blob.size <= 0) {
    throw new Error('Recording was empty — try again while the stream is live.');
  }
  const filename = buildLocalRecordingFilename(streamType, mime.extension);
  triggerDownload(blob, filename);
  return {filename, includedAudio: hadAudio};
}

/** Drop an in-progress recording without downloading (e.g. when the live session ends). */
export function discardLocalStreamRecording(): void {
  const recorder = activeRecorder;
  restoreAudioTracks();
  activeRecorder = null;
  activeChunks = [];
  activeMime = null;
  activeStreamType = null;
  includedAudio = false;
  if (!recorder) {
    return;
  }
  try {
    if (recorder.state !== 'inactive') {
      recorder.ondataavailable = null;
      recorder.onstop = null;
      recorder.stop();
    }
  } catch {
    /* already stopped */
  }
}
