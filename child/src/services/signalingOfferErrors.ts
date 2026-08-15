/**
 * Classify signaling POST failures for the child offer path.
 *
 * Production race (Oracle): parent stopScreenShare ends the session, then child
 * ICE recovery posts a new offer → HTTP 400 "Streaming session is not active".
 * Treating that as a hard failure caused noisy reportStreamFailure and poisoned
 * retry budgets. Duplicate offers (retried identical SDP) must be idempotent.
 */

export class StreamingSessionEndedError extends Error {
  constructor(message = 'Streaming session already ended') {
    super(message);
    this.name = 'StreamingSessionEndedError';
  }
}

const INACTIVE_SESSION_MARKERS = [
  'streaming session is not active',
  'streaming session has ended',
  'signaling unavailable',
];

const DUPLICATE_MARKERS = ['duplicate signaling message rejected'];

function errorText(error: unknown): string {
  if (error == null) {
    return '';
  }
  if (typeof error === 'string') {
    return error;
  }
  if (error instanceof Error) {
    const axiosData = (error as {response?: {data?: {message?: string; code?: string}}}).response
      ?.data;
    const parts = [error.message, axiosData?.message, axiosData?.code];
    return parts.filter(Boolean).join(' ');
  }
  if (typeof error === 'object') {
    const o = error as {message?: string; response?: {data?: {message?: string}}};
    return [o.message, o.response?.data?.message].filter(Boolean).join(' ');
  }
  return String(error);
}

export function isDuplicateSignalingError(error: unknown): boolean {
  const text = errorText(error).toLowerCase();
  return DUPLICATE_MARKERS.some(m => text.includes(m));
}

export function isInactiveStreamingSessionError(error: unknown): boolean {
  const text = errorText(error).toLowerCase();
  // Axios often only exposes "Request failed with status code 400" — callers that
  // have the response body should pass it; also match known backend messages.
  if (INACTIVE_SESSION_MARKERS.some(m => text.includes(m))) {
    return true;
  }
  return false;
}

export function isStreamingSessionEndedError(error: unknown): boolean {
  return (
    error instanceof StreamingSessionEndedError ||
    (error instanceof Error && error.name === 'StreamingSessionEndedError')
  );
}

/** Duplicate offer/answer → treat as success; inactive session → quiet end. */
export function classifyOfferPostFailure(
  error: unknown,
): 'duplicate_ok' | 'session_ended' | 'hard_failure' {
  if (isDuplicateSignalingError(error)) {
    return 'duplicate_ok';
  }
  if (isInactiveStreamingSessionError(error) || isStreamingSessionEndedError(error)) {
    return 'session_ended';
  }
  // Bare 400 without body: common when session ended and client only saw status.
  const axiosStatus = (error as {response?: {status?: number}})?.response?.status;
  const axiosMessage = (error as {response?: {data?: {message?: string}}})?.response?.data
    ?.message;
  if (axiosStatus === 400 && axiosMessage) {
    const lower = axiosMessage.toLowerCase();
    if (INACTIVE_SESSION_MARKERS.some(m => lower.includes(m))) {
      return 'session_ended';
    }
    if (DUPLICATE_MARKERS.some(m => lower.includes(m))) {
      return 'duplicate_ok';
    }
  }
  return 'hard_failure';
}
