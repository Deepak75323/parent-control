/** True when the remote stream currently has at least one non-ended audio track. */
export function streamHasLiveAudio(stream: MediaStream | null | undefined): boolean {
  if (!stream) {
    return false;
  }
  return stream.getAudioTracks().some(track => track.readyState !== 'ended');
}
