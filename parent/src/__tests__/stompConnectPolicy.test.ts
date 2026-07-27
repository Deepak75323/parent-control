import {shouldReuseStompClient} from '../services/stompConnectPolicy';

describe('shouldReuseStompClient', () => {
  it('does not reuse when client is only active (handshake in progress / hung)', () => {
    expect(
      shouldReuseStompClient({active: true, connected: false}, 'tok', 'tok'),
    ).toBe(false);
  });

  it('reuses when STOMP is connected with the same token', () => {
    expect(
      shouldReuseStompClient({active: true, connected: true}, 'tok', 'tok'),
    ).toBe(true);
  });

  it('does not reuse when the access token changed', () => {
    expect(
      shouldReuseStompClient({active: true, connected: true}, 'old', 'new'),
    ).toBe(false);
  });

  it('does not reuse when there is no client', () => {
    expect(shouldReuseStompClient(null, 'tok', 'tok')).toBe(false);
  });
});
