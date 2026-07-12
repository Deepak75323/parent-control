import {streamFailureMessage} from '../utils/streamFailureMessages';

describe('streamFailureMessage', () => {
  it('explains projection lost after restart', () => {
    expect(streamFailureMessage('PROJECTION_LOST_AFTER_RESTART')).toContain('re-approve');
  });

  it('explains recovery pending', () => {
    expect(streamFailureMessage('PROJECTION_RECOVERY_PENDING')).toContain('restoring');
  });

  it('explains recovery failed', () => {
    expect(streamFailureMessage('PROJECTION_RECOVERY_FAILED')).toContain('could not be restored');
  });

  it('explains missing screen consent', () => {
    expect(streamFailureMessage('NO_SCREEN_CAPTURE_CONSENT')).toContain('Permissions');
  });

  it('handles ICE failures', () => {
    expect(streamFailureMessage('ICE_failed')).toContain('Connection failed');
  });

  it('explains camera FGS failure', () => {
    expect(streamFailureMessage('CAMERA_FGS_FAILED')).toContain('Camera service');
  });

  it('explains audio focus denial', () => {
    expect(streamFailureMessage('AUDIO_FOCUS_DENIED')).toContain('microphone');
  });
});
