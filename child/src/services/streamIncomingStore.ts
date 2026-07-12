import {flowLog} from './flowLogger';

export type IncomingStreamRequest = {
  signalingRoomId: string;
  streamType: string;
  sessionId: string;
  cameraFacing?: string;
  traceId?: string;
  receivedAt: number;
};

let pending: IncomingStreamRequest | null = null;
const listeners = new Set<(req: IncomingStreamRequest | null) => void>();

export function setPendingStreamRequest(req: IncomingStreamRequest | null): void {
  pending = req;
  flowLog('SCREEN_SHARE_FLOW', 'pending stream request updated', {
    hasPending: !!req,
    sessionId: req?.sessionId,
    streamType: req?.streamType,
  });
  listeners.forEach(fn => fn(pending));
}

export function getPendingStreamRequest(): IncomingStreamRequest | null {
  return pending;
}

export function clearPendingStreamRequest(sessionId?: string): void {
  if (sessionId && pending && pending.sessionId !== sessionId) return;
  setPendingStreamRequest(null);
}

export function subscribePendingStream(
  listener: (req: IncomingStreamRequest | null) => void,
): () => void {
  listeners.add(listener);
  listener(pending);
  return () => listeners.delete(listener);
}
