type VideoFrameWithCvo = VideoFrame & {rotation?: number};

export type MediaStreamTrackProcessorLike = {
  readable: ReadableStream<VideoFrameWithCvo>;
};

export function createMediaStreamTrackProcessor(
  track: MediaStreamTrack,
): MediaStreamTrackProcessorLike | null {
  const ctor = (
    globalThis as typeof globalThis & {
      MediaStreamTrackProcessor?: new (init: {
        track: MediaStreamTrack;
      }) => MediaStreamTrackProcessorLike;
    }
  ).MediaStreamTrackProcessor;
  if (!ctor) {
    return null;
  }
  return new ctor({track});
}

export function supportsMediaStreamTrackProcessor(): boolean {
  return createMediaStreamTrackProcessor !== null && Boolean(
    (globalThis as typeof globalThis & {MediaStreamTrackProcessor?: unknown})
      .MediaStreamTrackProcessor,
  );
}
