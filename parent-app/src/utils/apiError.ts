import axios from 'axios';

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message;
    const status = error.response?.status;
    if (typeof message === 'string' && message.length > 0) {
      return status ? `HTTP ${status}: ${message}` : message;
    }
    if (!error.response) {
      return 'Cannot reach server. Check backend is running and config uses 10.0.2.2 on the emulator.';
    }
    return status ? `HTTP ${status}: ${fallback}` : fallback;
  }
  return fallback;
}
