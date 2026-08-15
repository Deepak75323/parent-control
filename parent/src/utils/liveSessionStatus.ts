export type LiveStreamType = 'SCREEN' | 'CAMERA' | 'AUDIO';

/** Derive live / pending / failed from ICE + track state (wifi↔cellular uses disconnected briefly). */
export function liveSessionUiState(opts: {
  iceState: string | null;
  hasRemoteStream: boolean;
  videoTrackEnded?: boolean;
  connecting: boolean;
  hasSession: boolean;
}): {isLive: boolean; isFailed: boolean; isPending: boolean; isReconnecting: boolean} {
  const ice = (opts.iceState ?? '').toLowerCase();
  const isLive = opts.hasRemoteStream && (ice === 'connected' || ice === 'completed');
  const isReconnecting = ice === 'disconnected' || ice === 'checking';
  // Only hard-fail on ICE failed / ended track — not on transient disconnected.
  const isFailed =
    !isLive && (ice === 'failed' || (!!opts.hasRemoteStream && !!opts.videoTrackEnded));
  const isPending =
    !isLive && !isFailed && (opts.connecting || opts.hasSession || isReconnecting);
  return {isLive, isFailed, isPending, isReconnecting};
}

function connectingLabel(
  streamType: LiveStreamType,
  captureMode?: string | null,
): string {
  if (streamType === 'AUDIO') return 'Connecting mic…';
  if (streamType === 'CAMERA') return 'Connecting camera…';
  if (streamType === 'SCREEN' && captureMode === 'RN_DISPLAY_MEDIA') {
    return 'Starting screen sharing…';
  }
  return 'Connecting…';
}

export function sessionStatusLabel(
  streamType: LiveStreamType,
  connecting: boolean,
  iceState: string | null,
  hasStream: boolean,
  captureMode?: string | null,
  wasEverLive?: boolean,
): string {
  const ice = (iceState ?? '').toLowerCase();
  if (hasStream && (ice === 'connected' || ice === 'completed')) {
    return 'Live';
  }
  // Real mid-session drop (Wi‑Fi↔cellular / brief offline). Not first-connect ICE.
  if (ice === 'disconnected') {
    return 'Connection lost · Reconnecting…';
  }
  if (ice === 'failed') {
    return 'Child unreachable · Connection lost';
  }
  // ICE "checking" is the normal first-connect handshake OR a mid-session restart.
  // Only call it Reconnecting if we've actually been live in this session.
  if (ice === 'checking') {
    if (wasEverLive) return 'Reconnecting…';
    return connectingLabel(streamType, captureMode);
  }
  if (connecting || ice === 'new') {
    return connectingLabel(streamType, captureMode);
  }
  if (streamType === 'AUDIO') return 'Waiting for audio…';
  if (streamType === 'CAMERA') return 'Waiting for camera…';
  if (streamType === 'SCREEN') return 'Waiting for screen…';
  return 'Waiting…';
}
