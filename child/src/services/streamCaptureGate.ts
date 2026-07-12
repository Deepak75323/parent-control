/** Prevents repeated MediaProjection / getDisplayMedia prompts for the same session. */

const DECLINE_BACKOFF_MS = 120_000;

let declinedSessionId: string | null = null;
let declinedUntil = 0;
let capturePromptActive = false;

export function setCapturePromptActive(active: boolean): void {
  capturePromptActive = active;
}

export function isCapturePromptActive(): boolean {
  return capturePromptActive;
}

export function markCaptureDeclined(sessionId: string, backoffMs = DECLINE_BACKOFF_MS): void {
  declinedSessionId = sessionId;
  declinedUntil = Date.now() + backoffMs;
}

export function isCaptureDeclined(sessionId: string): boolean {
  if (!declinedSessionId || declinedSessionId !== sessionId) {
    return false;
  }
  if (Date.now() > declinedUntil) {
    declinedSessionId = null;
    declinedUntil = 0;
    return false;
  }
  return true;
}

export function clearCaptureDecline(sessionId?: string): void {
  if (!sessionId || declinedSessionId === sessionId) {
    declinedSessionId = null;
    declinedUntil = 0;
  }
}
