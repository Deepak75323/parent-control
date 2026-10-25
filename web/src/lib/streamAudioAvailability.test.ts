import {describe, expect, it} from 'vitest';
import {streamHasLiveAudio} from './streamAudioAvailability';

function fakeStream(tracks: Array<{readyState: MediaStreamTrackState}>): MediaStream {
  return {
    getAudioTracks: () => tracks as MediaStreamTrack[],
  } as MediaStream;
}

describe('streamHasLiveAudio', () => {
  it('is false for null/empty', () => {
    expect(streamHasLiveAudio(null)).toBe(false);
    expect(streamHasLiveAudio(fakeStream([]))).toBe(false);
  });

  it('is true when a live track exists', () => {
    expect(streamHasLiveAudio(fakeStream([{readyState: 'live'}]))).toBe(true);
  });

  it('ignores ended tracks', () => {
    expect(streamHasLiveAudio(fakeStream([{readyState: 'ended'}]))).toBe(false);
  });
});
