import {attemptsAtConnectEntry} from '../connectionAttemptBudget';

describe('attemptsAtConnectEntry', () => {
  it('does not carry an exhausted budget into a new connect call', () => {
    // Production: after 3 failures, a new STREAM_REQUEST (new sessionId) reported
    // "Connection failed after maximum attempts" in ~200ms with no child offer.
    expect(attemptsAtConnectEntry(3)).toBe(0);
  });

  it('starts every connectChildStream invocation at zero', () => {
    expect(attemptsAtConnectEntry(0)).toBe(0);
    expect(attemptsAtConnectEntry(1)).toBe(0);
    expect(attemptsAtConnectEntry(2)).toBe(0);
  });
});
