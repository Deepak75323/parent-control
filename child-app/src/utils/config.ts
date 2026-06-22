/** STUN-only dev fallback — TURN credentials always come from /webrtc-config. */
export const devIceServers = [{urls: 'stun:stun.l.google.com:19302'}] as const;

export {getApiBaseUrl, getWsNativeUrl, resolveApiBaseUrl, setResolvedApiBaseUrl, isApiReachable} from './devApiResolver';

export const config = {
  enableSslPinning: process.env.ENABLE_SSL_PINNING === 'true',
  devIceServers,
};
