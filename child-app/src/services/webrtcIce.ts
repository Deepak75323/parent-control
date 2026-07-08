import {config} from '../utils/config';

export type IceConfig = {
  iceServers: RTCIceServer[];
  iceTransportPolicy: RTCIceTransportPolicy;
  credentialExpiresAt?: number;
};

function normalizeUrls(urls: string | string[]): string[] {
  return Array.isArray(urls) ? urls : [urls];
}

/** Split multi-URL iceServers so each transport can be selected independently in dev tooling. */
export function expandIceServersByUrl(servers: RTCIceServer[]): RTCIceServer[] {
  const expanded: RTCIceServer[] = [];
  for (const server of servers) {
    const urls = normalizeUrls(server.urls as string | string[]);
    if (urls.length <= 1) {
      expanded.push(server);
      continue;
    }
    for (const url of urls) {
      expanded.push({...server, urls: url});
    }
  }
  return expanded;
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
        .replace(/^turn:192\.168\.\d+\.\d+/i, 'turn:10.0.2.2')
        .replace(/^turns:127\.0\.0\.1/i, 'turns:10.0.2.2')
        .replace(/^turns:localhost/i, 'turns:10.0.2.2')
        .replace(/^turns:192\.168\.\d+\.\d+/i, 'turns:10.0.2.2');
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
  // Only force relay-only when the TURN server is reachable solely via a virtual/loopback
  // address (10.0.2.2 = Android-emulator alias for host loopback; 127.0.0.1/localhost).
  // LAN IPs (192.168.x.x) are reachable from a physical device via Wi-Fi, so the child
  // should use 'all' policy: host/srflx candidates allow a direct Wi-Fi path that bypasses
  // the broken hairpin TURN relay (both relays on the same host IP).
  return servers.some(server => {
    const urls = normalizeUrls(server.urls as string | string[]);
    return urls.some(
      url =>
        typeof url === 'string' &&
        (url.includes('10.0.2.2') ||
          url.includes('127.0.0.1') ||
          url.includes('localhost')),
    );
  });
}

export function logIce(label: string, state: string, detail?: string): void {
  if (!__DEV__) return;
  const suffix = detail ? ` (${detail})` : '';
  console.warn(`[webrtc:${label}] ${state}${suffix}`);
}

export function orderedSignalingMessages<T extends {id: string}>(messages: T[] = []): T[] {
  return [...messages];
}
