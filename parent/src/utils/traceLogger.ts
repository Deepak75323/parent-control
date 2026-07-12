/**
 * End-to-end stream trace logger — filter Metro with [TRACE_ID= or PCP_TRACE.
 * Every log includes TRACE_ID, timestamp, thread, state, function, component.
 */

export type TraceComponent =
  | 'Parent'
  | 'Camera'
  | 'Screen'
  | 'Audio'
  | 'WebRTC'
  | 'Socket'
  | 'TURN'
  | 'Notification'
  | 'API';

let activeTraceId: string | null = null;
let currentState = 'IDLE';
let stepCounter = 0;

function randomUuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function generateTraceId(): string {
  activeTraceId = randomUuid();
  stepCounter = 0;
  currentState = 'INIT';
  return activeTraceId;
}

export function setTraceId(id: string | null): void {
  activeTraceId = id;
}

export function getTraceId(): string | null {
  return activeTraceId;
}

export function setTraceState(state: string): void {
  currentState = state;
}

export function getTraceState(): string {
  return currentState;
}

export function traceHeaders(): Record<string, string> {
  const headers: Record<string, string> = {};
  if (activeTraceId) {
    headers['X-Trace-Id'] = activeTraceId;
  }
  return headers;
}

function formatLine(
  component: TraceComponent,
  fn: string,
  message: string,
  detail?: unknown,
  level: 'log' | 'warn' | 'error' = 'log',
): string {
  stepCounter += 1;
  const ts = new Date().toISOString();
  const trace = activeTraceId ? `[TRACE_ID=${activeTraceId}]` : '[TRACE_ID=none]';
  const extra =
    detail !== undefined
      ? ` ${typeof detail === 'object' ? JSON.stringify(detail) : String(detail)}`
      : '';
  return `${trace} [${component}] [${currentState}] [${fn}] #${String(stepCounter).padStart(4, '0')} ${ts} ${message}${extra}`;
}

export function traceLog(
  component: TraceComponent,
  fn: string,
  message: string,
  detail?: unknown,
): void {
  if (!__DEV__) return;
  const line = formatLine(component, fn, message, detail, 'log');
  console.log(line);
  console.warn(line);
}

export function traceWarn(
  component: TraceComponent,
  fn: string,
  message: string,
  detail?: unknown,
): void {
  if (!__DEV__) return;
  const line = formatLine(component, fn, message, detail, 'warn');
  console.warn(line);
}

export function traceError(
  component: TraceComponent,
  fn: string,
  message: string,
  err?: unknown,
): void {
  if (!__DEV__) return;
  stepCounter += 1;
  const ts = new Date().toISOString();
  const trace = activeTraceId ? `[TRACE_ID=${activeTraceId}]` : '[TRACE_ID=none]';
  const line = `${trace} [${component}] [${currentState}] [${fn}] #${String(stepCounter).padStart(4, '0')} ${ts} ERROR ${message}`;
  console.error(line);
  if (err instanceof Error) {
    console.error(`${trace} stack:`, err.stack ?? err.message);
  } else if (err !== undefined) {
    console.error(`${trace} detail:`, err);
  }
}

export interface FailureReport {
  component: TraceComponent;
  fn: string;
  input?: unknown;
  output?: unknown;
  expected?: string;
  actual?: string;
  reason?: string;
  nextCauses?: string[];
}

export function traceFailure(report: FailureReport, err?: unknown): void {
  if (!__DEV__) return;
  const trace = activeTraceId ?? 'none';
  const divider = '='.repeat(50);
  console.error(divider);
  console.error('FAILURE REPORT');
  console.error(divider);
  console.error(`TRACE_ID: ${trace}`);
  console.error(`Component: ${report.component}`);
  console.error(`Function: ${report.fn}`);
  console.error(`State: ${currentState}`);
  if (report.input !== undefined) console.error(`Input: ${JSON.stringify(report.input)}`);
  if (report.output !== undefined) console.error(`Output: ${JSON.stringify(report.output)}`);
  if (report.expected) console.error(`Expected: ${report.expected}`);
  if (report.actual) console.error(`Actual: ${report.actual}`);
  if (report.reason) console.error(`Reason: ${report.reason}`);
  if (report.nextCauses?.length) {
    console.error('Next possible causes:');
    report.nextCauses.forEach(c => console.error(`  - ${c}`));
  }
  if (err instanceof Error) {
    console.error('Stacktrace:', err.stack ?? err.message);
  }
  console.error(divider);
}
