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
  it('shows connecting camera during first-connect ICE checking', () => {
    // ICE "checking" is the normal handshake — not a drop.
    expect(sessionStatusLabel('CAMERA', true, 'checking', false)).toBe(
      'Connecting camera…',
    );
  });

  it('keeps connecting copy when stream arrives before ICE connected (never live yet)', () => {
    // ontrack can fire while ICE is still checking on first connect;
    // connecting is already false but we've never been live, so this is NOT a reconnect.
    expect(
      sessionStatusLabel('CAMERA', false, 'checking', true, undefined, false),
    ).toBe('Connecting camera…');
  });

  it('shows reconnecting only on ICE checking after we were live', () => {
    // Mid-session ICE restart: was live, ICE went back to checking.
    expect(
      sessionStatusLabel('CAMERA', false, 'checking', true, undefined, true),
    ).toBe('Reconnecting…');
  });

  it('shows connection lost only on ICE disconnected', () => {
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
