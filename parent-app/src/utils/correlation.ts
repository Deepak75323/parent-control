let sessionId: string | null = null;

function randomId(): string {
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 10)}`;
}

export function getCorrelationId(): string {
  if (!sessionId) {
    sessionId = randomId();
  }
  return sessionId;
}

export function newCorrelationSession(prefix = 'parent'): string {
  sessionId = `${prefix}_${randomId()}`;
  return sessionId;
}

export function correlationHeaders(): Record<string, string> {
  const headers: Record<string, string> = {'X-Correlation-Id': getCorrelationId()};
  // Lazy import avoids circular dependency with traceLogger
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const {getTraceId, traceHeaders} = require('./traceLogger') as typeof import('./traceLogger');
    const traceId = getTraceId();
    if (traceId) {
      Object.assign(headers, traceHeaders());
    }
  } catch {
    // traceLogger not loaded yet — correlation-only headers
  }
  return headers;
}
