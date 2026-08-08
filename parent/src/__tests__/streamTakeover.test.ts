import {shouldReleaseViewerOnStreamingEnded} from '../services/streamTakeover';

describe('shouldReleaseViewerOnStreamingEnded', () => {
  it('releases when ENDED matches the active room', () => {
    expect(
      shouldReleaseViewerOnStreamingEnded(
        {status: 'ENDED', roomId: 'screen_abc'},
        'screen_abc',
      ),
    ).toBe(true);
  });

  it('does not release when ENDED is for a different room (new viewer session)', () => {
    expect(
      shouldReleaseViewerOnStreamingEnded(
        {status: 'ENDED', roomId: 'screen_old'},
        'screen_new',
      ),
    ).toBe(false);
  });

  it('does not release on FAILED or ACTIVE', () => {
    expect(
      shouldReleaseViewerOnStreamingEnded(
        {status: 'FAILED', roomId: 'screen_abc'},
        'screen_abc',
      ),
    ).toBe(false);
    expect(
      shouldReleaseViewerOnStreamingEnded(
        {status: 'ACTIVE', roomId: 'screen_abc'},
        'screen_abc',
      ),
    ).toBe(false);
  });

  it('does not release when there is no active room', () => {
    expect(
      shouldReleaseViewerOnStreamingEnded({status: 'ENDED', roomId: 'screen_abc'}, null),
    ).toBe(false);
  });
});
