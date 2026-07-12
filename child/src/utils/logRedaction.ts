const REDACTED = '[REDACTED]';

const SENSITIVE_KEYS = new Set([
  'password',
  'childLoginPassword',
  'pin',
  'token',
  'accessToken',
  'refreshToken',
  'deviceToken',
  'deviceHmacKey',
  'hmacKey',
  'fcmToken',
  'androidId',
  'email',
  'phone',
  'title',
  'body',
  'notification',
  'latitude',
  'longitude',
  'recoveryCode',
  'pairingCode',
  'code',
  'authorization',
]);

function redactValue(key: string, value: unknown): unknown {
  if (SENSITIVE_KEYS.has(key)) {
    return REDACTED;
  }
  if (typeof value === 'string' && value.length > 64 && /^[A-Za-z0-9._-]+$/.test(value)) {
    return REDACTED;
  }
  return value;
}

export function redactForLogs(value: unknown): unknown {
  if (value == null || typeof value !== 'object') {
    return value;
  }
  if (Array.isArray(value)) {
    return value.map(item => redactForLogs(item));
  }
  const out: Record<string, unknown> = {};
  for (const [key, val] of Object.entries(value as Record<string, unknown>)) {
    if (val != null && typeof val === 'object') {
      out[key] = redactForLogs(val);
    } else {
      out[key] = redactValue(key, val);
    }
  }
  return out;
}

export function redactString(value: string): string {
  return value
    .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, REDACTED)
    .replace(/Bearer\s+[A-Za-z0-9._-]+/gi, `Bearer ${REDACTED}`);
}
