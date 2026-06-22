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
  return {'X-Correlation-Id': getCorrelationId()};
}
