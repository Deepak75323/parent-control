let stepCounter = 0;

function tracePrefix(): string {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const {getTraceId} = require('../utils/traceLogger') as typeof import('../utils/traceLogger');
    const id = getTraceId();
    return id ? `[TRACE_ID=${id}] ` : '';
  } catch {
    return '';
  }
}

/** Numbered step log — filter Metro with `[SCREEN:`. */
export function slog(tag: string, message: string, detail?: unknown): void {
  stepCounter += 1;
  const ts = new Date().toISOString().slice(11, 23);
  const extra = detail !== undefined ? ` ${JSON.stringify(detail)}` : '';
  const msg = `${tracePrefix()}[SCREEN:${String(stepCounter).padStart(4, '0')}:${tag}] ${ts} ${message}${extra}`;
  console.log(msg);
  console.warn(msg);
}

export function slogReset(): void {
  stepCounter = 0;
  slog('INIT', 'stream debug counter reset');
}

/** Structured checkpoint log — filter with PCP:WEBRTC_FLOW */
export function flowLog(tag: string, message: string, detail?: unknown): void {
  stepCounter += 1;
  const ts = new Date().toISOString();
  const extra = detail !== undefined ? ` ${JSON.stringify(detail)}` : '';
  const line = `${tracePrefix()}[PCP:${tag}] #${String(stepCounter).padStart(4, '0')} ${ts} ${message}${extra}`;
  console.log(line);
  console.warn(line);
}
