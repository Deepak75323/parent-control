import {Platform} from 'react-native';
import {config} from '../utils/config';

export type IceConfig = {
  iceServers: RTCIceServer[];
  /** Emulator ↔ physical phone always needs TURN; keep relay-only in dev. */
  iceTransportPolicy: RTCIceTransportPolicy;
  credentialExpiresAt?: number;
  /** P2-4: streaming session this config's TURN credential is bound to (if any). */
  sessionId?: string;
};

function normalizeUrls(urls: string | string[]): string[] {
  return Array.isArray(urls) ? urls : [urls];
}

/** Emulator maps 10.0.2.2 → host; rewrite loopback TURN URLs from the API. */
export function adaptIceServersForDev(
  servers: RTCIceServer[],
  apiBaseUrl: string,
): RTCIceServer[] {
  if (!__DEV__) {
    return servers;
  }
  let apiHost = '';
  try {
    apiHost = new URL(apiBaseUrl).hostname;
  } catch {
    return servers;
  }
  if (apiHost !== '10.0.2.2') {
    return servers;
  }
  return servers.map(server => {
    const urls = normalizeUrls(server.urls as string | string[]);
    const adapted = urls.map(url => {
      if (typeof url !== 'string' || !url.startsWith('turn')) {
        return url;
      }
      return url
        .replace(/^turn:127\.0\.0\.1/i, 'turn:10.0.2.2')
        .replace(/^turn:localhost/i, 'turn:10.0.2.2')
        .replace(/^turns:127\.0\.0\.1/i, 'turns:10.0.2.2')
        .replace(/^turns:localhost/i, 'turns:10.0.2.2');
    });
    return {
      ...server,
      urls: adapted.length === 1 ? adapted[0] : adapted,
    };
  });
}

function serverKey(server: RTCIceServer): string {
  const urls = normalizeUrls(server.urls as string | string[]).sort().join('|');
  return `${urls}::${server.username ?? ''}`;
}

function hasTurnCredentials(server: RTCIceServer): boolean {
  return Boolean(server.username && server.credential);
}

/** Merge dev STUN (dev only) + API ICE entries without duplicates. */
export function mergeIceServers(apiServers: RTCIceServer[] = []): RTCIceServer[] {
  const merged = new Map<string, RTCIceServer>();
  const seed = (__DEV__ ? [...config.devIceServers, ...apiServers] : apiServers) as RTCIceServer[];
  for (const server of seed) {
    merged.set(serverKey(server), server);
  }
  return [...merged.values()];
}

export function defaultIceConfig(
  apiServers: RTCIceServer[] = [],
  credentialExpiresAt?: number,
): IceConfig {
  const iceServers = mergeIceServers(apiServers);
  if (!__DEV__ && !iceServers.some(hasTurnCredentials)) {
    logIce('config', 'warning', 'no ephemeral TURN credentials from API');
  }
  return {
    iceServers,
    iceTransportPolicy: usesLocalDevTurn(iceServers) ? 'relay' : 'all',
    credentialExpiresAt,
  };
}

export function isIceConfigStale(ice: IceConfig | null, refreshSkewSeconds = 300): boolean {
  if (!ice?.credentialExpiresAt) {
    return false;
  }
  const now = Math.floor(Date.now() / 1000);
  return ice.credentialExpiresAt - now <= refreshSkewSeconds;
}

function usesLocalDevTurn(servers: RTCIceServer[]): boolean {
  return servers.some(server => {
    const urls = normalizeUrls(server.urls as string | string[]);
    return urls.some(
      url =>
        typeof url === 'string' &&
        (url.includes('10.0.2.2') ||
          url.includes('127.0.0.1') ||
          url.includes('192.168.') ||
          url.includes('localhost')),
    );
  });
}

export function logIce(label: string, state: string, detail?: string): void {
  if (!__DEV__) return;
  const suffix = detail ? ` (${detail})` : '';
  console.warn(`[webrtc:${label}] ${state}${suffix}`);
}

/** Backend already returns chronological order — do not reverse again. */
export function orderedSignalingMessages<T extends {id: string}>(messages: T[] = []): T[] {
  return [...messages];
}
