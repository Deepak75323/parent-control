/**
 * Dev API host:
 * - Android emulator: 10.0.2.2 (maps to host localhost)
 * - Physical phone (Wi-Fi): Mac LAN IP e.g. 192.168.1.8
 * - USB + adb reverse: 127.0.0.1
 */
const DEV_HOST = '10.0.2.2';

function deriveWsNativeUrl(apiBaseUrl: string): string {
  const trimmed = apiBaseUrl.replace(/\/$/, '');
  if (trimmed.startsWith('https://')) {
    return `${trimmed.replace(/^https/, 'wss')}/ws-native`;
  }
  return `${trimmed.replace(/^http/, 'ws')}/ws-native`;
}

const defaultApiBase = process.env.API_BASE_URL ?? `http://${DEV_HOST}:8080`;

/** STUN-only dev fallback — TURN credentials always come from /webrtc-config. */
export const devIceServers = [{urls: 'stun:stun.l.google.com:19302'}] as const;

export const config = {
  apiBaseUrl: defaultApiBase,
  wsBaseUrl: process.env.WS_BASE_URL ?? `${defaultApiBase}/ws`,
  wsNativeUrl: process.env.WS_NATIVE_URL ?? deriveWsNativeUrl(defaultApiBase),
  enableSslPinning: process.env.ENABLE_SSL_PINNING === 'true',
  devIceServers,
};
