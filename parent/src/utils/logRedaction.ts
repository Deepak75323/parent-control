export const REDACTED = '[REDACTED]';

const SENSITIVE_KEYS = new Set([
  'password',
  'childLoginPassword',
  'pin',
  'accessToken',
  'refreshToken',
  'deviceToken',
  'deviceHmacKey',
  'authorization',
]);

export function redactBody(body: string): string {
  try {
    const parsed = JSON.parse(body) as Record<string, unknown>;
    const redacted: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(parsed)) {
      redacted[key] = SENSITIVE_KEYS.has(key) ? REDACTED : value;
    }
    return JSON.stringify(redacted, null, 2);
  } catch {
    return body.replace(/Bearer\s+[A-Za-z0-9._-]+/gi, `Bearer ${REDACTED}`);
  }
}
