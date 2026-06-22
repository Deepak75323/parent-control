/**
 * P1-7: Child stream session ownership — single JS session authority aligned with native registry.
 */
export type StreamSessionPhase =
  | 'IDLE'
  | 'CONNECTING'
  | 'NEGOTIATING'
  | 'ACTIVE'
  | 'RECONNECTING'
  | 'TEARDOWN';

export type StreamOwner = 'NONE' | 'JS_HIDDEN' | 'NATIVE_PUBLISHER';

export type StreamSessionSnapshot = {
  correlationId: string | null;
  phase: StreamSessionPhase;
  owner: StreamOwner;
  roomId: string | null;
  sessionId: string | null;
};

let correlationId: string | null = null;
let phase: StreamSessionPhase = 'IDLE';
let owner: StreamOwner = 'NONE';
let roomId: string | null = null;
let sessionId: string | null = null;

function newCorrelationId(): string {
  return `ch-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export function beginJsSession(nextRoomId: string, nextSessionId: string): string {
  if (sessionId && sessionId !== nextSessionId) {
    endSession();
  }
  correlationId = newCorrelationId();
  phase = 'CONNECTING';
  owner = 'JS_HIDDEN';
  roomId = nextRoomId;
  sessionId = nextSessionId;
  return correlationId;
}

export function setSessionPhase(next: StreamSessionPhase): void {
  phase = next;
}

export function markJsActive(): void {
  phase = 'ACTIVE';
  owner = 'JS_HIDDEN';
}

export function syncFromNativeSnapshot(
  nativeSessionId: string | null,
  nativeOwner: string | null,
  nativeRunning: boolean,
): void {
  if (nativeOwner === 'NATIVE_PUBLISHER' && nativeRunning) {
    owner = 'NATIVE_PUBLISHER';
    sessionId = nativeSessionId;
    phase = 'ACTIVE';
    return;
  }
  if (owner === 'NATIVE_PUBLISHER' && !nativeRunning) {
    owner = 'NONE';
    if (phase === 'ACTIVE') {
      phase = 'IDLE';
    }
    sessionId = null;
    roomId = null;
  }
}

/** @deprecated Use syncFromNativeSnapshot */
export function syncNativePublisher(session: string | null, nativeRunning: boolean): void {
  syncFromNativeSnapshot(session, nativeRunning ? 'NATIVE_PUBLISHER' : 'NONE', nativeRunning);
}

export function endSession(): void {
  phase = 'TEARDOWN';
  owner = 'NONE';
  roomId = null;
  sessionId = null;
  correlationId = null;
  phase = 'IDLE';
}

export function getSessionSnapshot(): StreamSessionSnapshot {
  return {correlationId, phase, owner, roomId, sessionId};
}

export function isJsOwner(): boolean {
  return owner === 'JS_HIDDEN';
}
