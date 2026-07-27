/**
 * P1-7: Parent viewer session ownership — scoped listener registry (no stale singleton callbacks).
 */
import {MediaStream} from 'react-native-webrtc';

export type StreamSessionPhase =
  | 'IDLE'
  | 'CONNECTING'
  | 'NEGOTIATING'
  | 'ACTIVE'
  | 'RECONNECTING'
  | 'TEARDOWN';

export type StreamSessionSnapshot = {
  correlationId: string | null;
  phase: StreamSessionPhase;
  roomId: string | null;
  sessionId: string | undefined;
};

type StreamListeners = {
  onRemoteStream?: (stream: MediaStream) => void;
  onConnectionState?: (state: string) => void;
};

const listeners = new Map<symbol, StreamListeners>();
let activeToken: symbol | null = null;

let correlationId: string | null = null;
let phase: StreamSessionPhase = 'IDLE';
let roomId: string | null = null;
let sessionId: string | undefined;
/**
 * Survives endSession/disconnectViewer so logout / screen unmount can still call
 * stopScreenShare after local WebRTC state was cleared (otherwise child keeps cam/mic).
 */
let stickySessionId: string | undefined;

function newCorrelationId(): string {
  return `pv-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function activeHandlers(): StreamListeners {
  if (!activeToken) return {};
  return listeners.get(activeToken) ?? {};
}

export function beginSession(nextRoomId: string, nextSessionId?: string): string {
  correlationId = newCorrelationId();
  phase = 'CONNECTING';
  roomId = nextRoomId;
  sessionId = nextSessionId;
  if (nextSessionId) {
    stickySessionId = nextSessionId;
  }
  return correlationId;
}

export function setSessionPhase(next: StreamSessionPhase): void {
  phase = next;
}

export function endSession(): void {
  phase = 'TEARDOWN';
  roomId = null;
  sessionId = undefined;
  correlationId = null;
  phase = 'IDLE';
  // Keep stickySessionId — consumed by backend stop helpers.
}

export function getSessionSnapshot(): StreamSessionSnapshot {
  return {correlationId, phase, roomId, sessionId};
}

/** Session id still needing a backend stop (live or sticky after local disconnect). */
export function getSessionIdForBackendStop(): string | undefined {
  return sessionId ?? stickySessionId;
}

/** Take sticky/live session id once — used after a best-effort stopScreenShare. */
export function takeSessionIdForBackendStop(): string | undefined {
  const id = sessionId ?? stickySessionId;
  stickySessionId = undefined;
  sessionId = undefined;
  return id;
}

/** Test helper — drop sticky without calling the API. */
export function clearStickySessionId(): void {
  stickySessionId = undefined;
}

/** Register screen-scoped listeners; returns unsubscribe that removes stale callbacks on navigation. */
export function registerStreamListeners(
  handlers: StreamListeners,
  scopeLabel = 'live-monitoring',
): () => void {
  const token = Symbol(scopeLabel);
  listeners.set(token, handlers);
  activeToken = token;
  return () => {
    listeners.delete(token);
    if (activeToken === token) {
      const keys = [...listeners.keys()];
      activeToken = keys.length > 0 ? keys[keys.length - 1]! : null;
    }
  };
}

/** @deprecated Use registerStreamListeners — kept for migration. */
export function setOnRemoteStream(cb: (stream: MediaStream) => void): void {
  registerStreamListeners({onRemoteStream: cb}, 'legacy-setOnRemoteStream');
}

/** @deprecated Use registerStreamListeners */
export function setOnConnectionState(cb: (state: string) => void): void {
  const existing = activeHandlers();
  registerStreamListeners({...existing, onConnectionState: cb}, 'legacy-setOnConnectionState');
}

export function emitRemoteStream(stream: MediaStream): void {
  activeHandlers().onRemoteStream?.(stream);
}

export function emitConnectionState(state: string): void {
  activeHandlers().onConnectionState?.(state);
}

export function clearAllStreamListeners(): void {
  listeners.clear();
  activeToken = null;
}
