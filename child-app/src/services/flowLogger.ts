/**
 * Structured flow logger — filter Logcat/Metro with [PCP:TAG] or [SCREEN:
 */
export type FlowTag =
  | 'ONBOARDING'
  | 'SCREEN_SHARE_FLOW'
  | 'WEBRTC_FLOW'
  | 'AUDIO_FLOW'
  | 'PERMISSION_FLOW'
  | 'MEDIA_PROJECTION'
  | 'SOCKET_SIGNALING'
  | 'SERVICE_STATE'
  | 'UI'
  | 'POLL'
  | 'ICE'
  | 'SDP'
  | 'CAPTURE'
  | 'STREAM'
  | 'FLOW'
  | 'DIAG';

let stepCounter = 0;
let sessionCorrelationId: string | null = null;
let activeTraceId: string | null = null;
let currentState = 'IDLE';

export function flowSetTraceId(id: string | null): void {
  activeTraceId = id;
  if (id) sessionCorrelationId = id;
}

export function flowGetTraceId(): string | null {
  return activeTraceId;
}

export function flowSetState(state: string): void {
  currentState = state;
}

export function flowSetSession(sessionId: string | null): void {
  sessionCorrelationId = sessionId;
}

export function flowNewSession(prefix = 'sess'): string {
  sessionCorrelationId = `${prefix}_${Date.now().toString(36)}`;
  return sessionCorrelationId;
}

export function flowGetSession(): string | null {
  return sessionCorrelationId;
}

export function flowLog(
  tag: FlowTag,
  message: string,
  detail?: Record<string, unknown> | unknown,
  fn = 'unknown',
): void {
  stepCounter += 1;
  const ts = new Date().toISOString();
  const trace = activeTraceId ? `[TRACE_ID=${activeTraceId}]` : '[TRACE_ID=none]';
  const extra =
    detail !== undefined
      ? ` ${typeof detail === 'object' && detail !== null ? JSON.stringify(detail) : String(detail)}`
      : '';
  const line = `${trace} [Child] [${currentState}] [${fn}] [PCP:${tag}] #${String(stepCounter).padStart(4, '0')} ${ts} ${message}${extra}`;
  console.log(line);
  console.warn(line);
}

export function flowFailure(
  tag: FlowTag,
  fn: string,
  report: {
    input?: unknown;
    output?: unknown;
    expected?: string;
    actual?: string;
    reason?: string;
    nextCauses?: string[];
  },
  err?: unknown,
): void {
  const divider = '='.repeat(50);
  console.error(divider);
  console.error('FAILURE REPORT');
  console.error(divider);
  console.error(`TRACE_ID: ${activeTraceId ?? 'none'}`);
  console.error(`Component: Child`);
  console.error(`Function: ${fn}`);
  console.error(`Category: ${tag}`);
  console.error(`State: ${currentState}`);
  if (report.input !== undefined) console.error(`Input: ${JSON.stringify(report.input)}`);
  if (report.output !== undefined) console.error(`Output: ${JSON.stringify(report.output)}`);
  if (report.expected) console.error(`Expected: ${report.expected}`);
  if (report.actual) console.error(`Actual: ${report.actual}`);
  if (report.reason) console.error(`Reason: ${report.reason}`);
  report.nextCauses?.forEach(c => console.error(`Next cause: ${c}`));
  if (err instanceof Error) console.error('Stacktrace:', err.stack ?? err.message);
  console.error(divider);
}

export function flowReset(): void {
  stepCounter = 0;
  sessionCorrelationId = null;
  activeTraceId = null;
  currentState = 'IDLE';
  flowLog('FLOW', 'logger reset');
}

/** Back-compat alias used across streaming code. */
export function slog(tag: string, message: string, detail?: unknown): void {
  const mapped = (
    ['ICE', 'SDP', 'CAPTURE', 'STREAM', 'POLL', 'WS', 'PC', 'MONITOR', 'SIGNAL', 'CAMERA', 'PERMS', 'UI', 'APP'].includes(tag)
      ? tag === 'PERMS'
        ? 'PERMISSION_FLOW'
        : tag === 'WS' || tag === 'SIGNAL'
          ? 'SOCKET_SIGNALING'
          : tag === 'CAPTURE'
            ? 'MEDIA_PROJECTION'
            : tag === 'STREAM' || tag === 'FLOW'
              ? 'SCREEN_SHARE_FLOW'
              : tag === 'PC' || tag === 'ICE' || tag === 'SDP' || tag === 'MONITOR'
                ? 'WEBRTC_FLOW'
                : 'UI'
      : 'FLOW'
  ) as FlowTag;
  flowLog(mapped, `[${tag}] ${message}`, detail as Record<string, unknown> | undefined);
}

export function slogReset(): void {
  flowReset();
}
