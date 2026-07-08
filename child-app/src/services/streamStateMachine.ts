/**
 * Unified stream lifecycle state machine — filter Metro with [STREAM_STATE]
 */
import {flowGetTraceId, flowLog} from './flowLogger';

export type StreamState =
  | 'IDLE'
  | 'REQUEST_SENT'
  | 'REQUEST_RECEIVED'
  | 'SERVICE_STARTING'
  | 'SERVICE_STARTED'
  | 'PERMISSION_VERIFIED'
  | 'CAPTURE_STARTING'
  | 'CAPTURE_RUNNING'
  | 'WEBRTC_CONNECTING'
  | 'LOCAL_DESCRIPTION_SET'
  | 'REMOTE_DESCRIPTION_SET'
  | 'ICE_CONNECTING'
  | 'CONNECTED'
  | 'FIRST_FRAME_SENT'
  | 'FIRST_FRAME_RECEIVED'
  | 'STREAMING'
  | 'STOPPING'
  | 'STOPPED'
  | 'FAILED';

let currentStreamState: StreamState = 'IDLE';
let previousStreamState: StreamState = 'IDLE';

export function getStreamState(): StreamState {
  return currentStreamState;
}

export function resetStreamState(): void {
  previousStreamState = 'IDLE';
  currentStreamState = 'IDLE';
}

export function streamStateTransition(
  next: StreamState,
  fn: string,
  detail?: Record<string, unknown>,
): void {
  const from = currentStreamState;
  previousStreamState = from;
  currentStreamState = next;
  const trace = flowGetTraceId() ? `[TRACE_ID=${flowGetTraceId()}]` : '[TRACE_ID=none]';
  const extra = detail ? ` ${JSON.stringify(detail)}` : '';
  const line = `${trace} [STREAM_STATE] ${from} → ${next} [${fn}]${extra}`;
  console.log(line);
  console.warn(line);
  flowLog('STREAM', `STREAM_STATE ${from} → ${next}`, detail, fn);
}
