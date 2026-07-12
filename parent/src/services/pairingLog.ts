/** Structured pairing lifecycle logs — visible in Metro / device logcat. */
export function plog(stage: string, detail?: unknown): void {
  const msg = detail === undefined ? `[PAIR] ${stage}` : `[PAIR] ${stage} ${JSON.stringify(detail)}`;
  if (__DEV__) {
    console.warn(msg);
  }
}
