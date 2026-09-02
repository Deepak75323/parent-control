import {describe, expect, it} from 'vitest';
import {streamFailureMessage} from './streamFailureMessages';

describe('streamFailureMessage', () => {
  it('explains camera start failure without service jargon', () => {
    const msg = streamFailureMessage('CAMERA_FGS_FAILED');
    expect(msg).toContain('Camera view');
    expect(msg).not.toMatch(/FGS|service/i);
  });

  it('explains camera blocked by another app', () => {
    const msg = streamFailureMessage('CAMERA_IN_USE_BY_OTHER_APP');
    expect(msg).toMatch(/another app/i);
    expect(msg).toMatch(/Reconnect/i);
  });

  it('explains child did not answer (no offer)', () => {
    expect(streamFailureMessage('CHILD_NO_OFFER')).toMatch(/didn.t answer/i);
  });
});
