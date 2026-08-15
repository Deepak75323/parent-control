/**
 * Child WebRTC connect retry budget.
 *
 * Production bug (Oracle staging): `connectionAttempts` lived across sessions and
 * only reset on success. After MAX failures, a later STREAM_REQUEST with a new
 * sessionId failed immediately (~200ms) with "Connection failed after maximum
 * attempts" and never posted an offer.
 *
 * Each `connectChildStream()` call must start at 0; in-call retries increment
 * the module counter inside the implementation.
 */
export function attemptsAtConnectEntry(_previousAttempts: number): number {
  return 0;
}
