let stepCounter = 0;

/** Numbered step log — filter Metro with `[SCREEN:`. */
export function slog(tag: string, message: string, detail?: unknown): void {
  stepCounter += 1;
  const ts = new Date().toISOString().slice(11, 23);
  const extra = detail !== undefined ? ` ${JSON.stringify(detail)}` : '';
  const msg = `[SCREEN:${String(stepCounter).padStart(4, '0')}:${tag}] ${ts} ${message}${extra}`;
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
  const line = `[PCP:${tag}] #${String(stepCounter).padStart(4, '0')} ${ts} ${message}${extra}`;
  console.log(line);
  console.warn(line);
}
