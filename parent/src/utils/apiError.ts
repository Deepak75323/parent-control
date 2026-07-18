import axios from 'axios';

/** User-facing API error text — never expose HTTP codes, env vars, or stack-style details. */
export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message;
    if (typeof message === 'string' && message.length > 0 && !looksTechnical(message)) {
      return message;
    }
    if (!error.response || !error.response.status) {
      return 'No internet connection. Check Wi‑Fi or mobile data and try again.';
    }
    if (error.response.status === 429) {
      return 'Too many tries. Wait a minute, then try again.';
    }
    if (error.response.status >= 500) {
      return 'Something went wrong on our side. Please try again in a moment.';
    }
    return fallback;
  }
  if (error instanceof Error && error.message && !looksTechnical(error.message)) {
    return error.message;
  }
  return fallback;
}

function looksTechnical(message: string): boolean {
  return /HTTP\s*\d|API_BASE_URL|ECONNREFUSED|ETIMEDOUT|stack|Exception|MediaProjection|token|ICE|TURN/i.test(
    message,
  );
}
