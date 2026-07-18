import {streamFailureMessage} from '../utils/streamFailureMessages';

describe('streamFailureMessage', () => {
  it('explains projection lost after restart', () => {
    expect(streamFailureMessage('PROJECTION_LOST_AFTER_RESTART')).toContain('Start Screen');
  });

  it('explains recovery pending', () => {
    expect(streamFailureMessage('PROJECTION_RECOVERY_PENDING')).toContain('Start Screen');
  });

  it('explains recovery failed', () => {
    expect(streamFailureMessage('PROJECTION_RECOVERY_FAILED')).toContain('Start Screen');
  });

  it('explains missing screen consent', () => {
    expect(streamFailureMessage('NO_SCREEN_CAPTURE_CONSENT')).toContain('Start Screen');
  });

  it('explains accessibility disabled without jargon', () => {
    const msg = streamFailureMessage('ACCESSIBILITY_DISABLED');
    expect(msg).toContain('permissions');
    expect(msg).not.toMatch(/Accessibility/i);
  });

  it('handles ICE failures', () => {
    expect(streamFailureMessage('ICE_failed')).toContain('Connection failed');
  });

  it('explains camera start failure without service jargon', () => {
    const msg = streamFailureMessage('CAMERA_FGS_FAILED');
    expect(msg).toContain('Camera view');
    expect(msg).not.toMatch(/FGS|service/i);
  });

  it('explains screen start failure without capture-service jargon', () => {
    const msg = streamFailureMessage('FGS_GATE_CLOSED');
    expect(msg).toContain('Screen sharing');
    expect(msg).not.toMatch(/FGS|capture service/i);
  });

  it('explains NO_CAPTURE_FRAMES', () => {
    expect(streamFailureMessage('NO_CAPTURE_FRAMES')).toContain('no screen picture');
  });
});
