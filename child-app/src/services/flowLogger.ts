/**
 * Structured flow logger — filter Logcat/Metro with [PCP:TAG] or [SCREEN:
 */
export type FlowTag =
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
): void {
  stepCounter += 1;
  const ts = new Date().toISOString();
  const corr = sessionCorrelationId ? ` corr=${sessionCorrelationId}` : '';
  const extra =
    detail !== undefined
      ? ` ${typeof detail === 'object' && detail !== null ? JSON.stringify(detail) : String(detail)}`
      : '';
  const line = `[PCP:${tag}] #${String(stepCounter).padStart(4, '0')}${corr} ${ts} ${message}${extra}`;
  console.log(line);
  console.warn(line);
}

export function flowReset(): void {
  stepCounter = 0;
  sessionCorrelationId = null;
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
