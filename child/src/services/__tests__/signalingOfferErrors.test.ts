import {
  StreamingSessionEndedError,
  classifyOfferPostFailure,
  isDuplicateSignalingError,
  isInactiveStreamingSessionError,
  isStreamingSessionEndedError,
} from '../signalingOfferErrors';

describe('signalingOfferErrors', () => {
  it('treats duplicate signaling rejection as idempotent success', () => {
    const err = {
      message: 'Request failed with status code 400',
      response: {status: 400, data: {message: 'Duplicate signaling message rejected'}},
    };
    expect(isDuplicateSignalingError(err)).toBe(true);
    expect(classifyOfferPostFailure(err)).toBe('duplicate_ok');
  });

  it('treats inactive session 400 as quiet session end (parent stop race)', () => {
    // Production: stopScreenShare → ENDED, then child iceRestart offer → 400
    const err = {
      message: 'Request failed with status code 400',
      response: {status: 400, data: {message: 'Streaming session is not active'}},
    };
    expect(isInactiveStreamingSessionError(err)).toBe(true);
    expect(classifyOfferPostFailure(err)).toBe('session_ended');
  });

  it('treats signaling-unavailable ended message as session end', () => {
    const err = {
      response: {
        status: 400,
        data: {message: 'Signaling unavailable — streaming session has ended'},
      },
    };
    expect(classifyOfferPostFailure(err)).toBe('session_ended');
  });

  it('recognizes StreamingSessionEndedError', () => {
    const err = new StreamingSessionEndedError();
    expect(isStreamingSessionEndedError(err)).toBe(true);
    expect(classifyOfferPostFailure(err)).toBe('session_ended');
  });

  it('keeps unrelated failures hard', () => {
    expect(classifyOfferPostFailure(new Error('Signaling API timeout'))).toBe('hard_failure');
    expect(
      classifyOfferPostFailure({
        response: {status: 500, data: {message: 'boom'}},
      }),
    ).toBe('hard_failure');
  });
});
