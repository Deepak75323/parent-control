import {liveSessionUiState, sessionStatusLabel} from '../utils/liveSessionStatus';

describe('liveSessionUiState', () => {
  it('treats ICE disconnected as reconnecting, not failed', () => {
    const s = liveSessionUiState({
      iceState: 'disconnected',
      hasRemoteStream: true,
      connecting: false,
      hasSession: true,
    });
    expect(s.isLive).toBe(false);
    expect(s.isFailed).toBe(false);
    expect(s.isReconnecting).toBe(true);
    expect(s.isPending).toBe(true);
  });

  it('treats ICE failed as failed', () => {
    const s = liveSessionUiState({
      iceState: 'failed',
      hasRemoteStream: false,
      connecting: false,
      hasSession: true,
    });
    expect(s.isFailed).toBe(true);
    expect(s.isPending).toBe(false);
  });

  it('is live when connected with a stream', () => {
    const s = liveSessionUiState({
      iceState: 'connected',
      hasRemoteStream: true,
      connecting: false,
      hasSession: true,
    });
    expect(s.isLive).toBe(true);
  });
});

describe('sessionStatusLabel', () => {
  it('shows reconnecting copy on disconnected', () => {
    expect(sessionStatusLabel('CAMERA', false, 'disconnected', false)).toBe(
      'Connection lost · Reconnecting…',
    );
  });

  it('shows unreachable only on failed', () => {
    expect(sessionStatusLabel('CAMERA', false, 'failed', false)).toBe(
      'Child unreachable · Connection lost',
    );
  });
});
