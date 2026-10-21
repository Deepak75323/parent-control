import {describe, expect, it} from 'vitest';
import {
  buildLocalRecordingFilename,
  pickLocalRecordingMime,
} from './streamLocalRecording';

describe('pickLocalRecordingMime', () => {
  it('prefers webm with opus for camera so the room mic is kept', () => {
    const picked = pickLocalRecordingMime('CAMERA', mime => mime === 'video/webm;codecs=vp8,opus');
    expect(picked).toEqual({
      mimeType: 'video/webm;codecs=vp8,opus',
      extension: 'webm',
    });
  });

  it('falls back to a generic container when no codec is listed as supported', () => {
    const picked = pickLocalRecordingMime('SCREEN', () => false);
    expect(picked).toEqual({mimeType: '', extension: 'webm'});
  });

  it('picks an audio container for audio-only streams', () => {
    const picked = pickLocalRecordingMime('AUDIO', mime => mime === 'audio/webm;codecs=opus');
    expect(picked).toEqual({
      mimeType: 'audio/webm;codecs=opus',
      extension: 'webm',
    });
  });
});

describe('buildLocalRecordingFilename', () => {
  it('builds a stable download name from stream type and timestamp', () => {
    const name = buildLocalRecordingFilename(
      'CAMERA',
      'webm',
      new Date('2026-10-17T12:34:56.789Z'),
    );
    expect(name).toBe('parentcontrol-camera-2026-10-17_12-34-56.webm');
  });
});
