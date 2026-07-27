import {Platform} from 'react-native';
import {config} from '../utils/config';
import {slog} from './streamDebug';

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

function isAndroidEmulator(): boolean {
  if (Platform.OS !== 'android') {
    return false;
  }
  const fingerprint = String(Platform.constants?.Fingerprint ?? '');
  const model = String(Platform.constants?.Model ?? '');
  return (
    fingerprint.includes('generic') ||
    fingerprint.includes('emu64') ||
    model.includes('sdk_gphone') ||
    model.includes('Emulator')
  );
}

/** Local loopback API → rewrite TURN hosts to 10.0.2.2 + adb reverse. */
function isEmulatorLocalApi(apiBaseUrl: string): boolean {
  if (!__DEV__) {
    return false;
  }
  try {
    const host = new URL(apiBaseUrl).hostname;
    return host === '10.0.2.2' || host === '127.0.0.1' || host === 'localhost';
  } catch {
    return false;
  }
}

function hasTurnServer(servers: RTCIceServer[]): boolean {
  return servers.some(server => {
    const urls = normalizeUrls(server.urls as string | string[]);
    return urls.some(
      url => typeof url === 'string' && (url.startsWith('turn:') || url.startsWith('turns:')),
    );
  });
}

/** One ICE URL per RTCIceServer — required so emulator can drop UDP TURN without keeping UDP in a multi-URL entry. */
export function expandIceServersByUrl(servers: RTCIceServer[]): RTCIceServer[] {
  const expanded: RTCIceServer[] = [];
  for (const server of servers) {
    const urls = normalizeUrls(server.urls as string | string[]);
    if (urls.length <= 1) {
      expanded.push(server);
      continue;
    }
    for (const url of urls) {
      expanded.push({
        ...server,
        urls: url,
      });
    }
  }
  return expanded;
}

function rewriteTurnHostForEmulator(url: string): string {
  if (!url.startsWith('turn') && !url.startsWith('turns')) {
    return url;
  }
  return url
    .replace(/^turn:127\.0\.0\.1/i, 'turn:10.0.2.2')
    .replace(/^turn:localhost/i, 'turn:10.0.2.2')
    .replace(/^turn:192\.168\.\d+\.\d+/i, 'turn:10.0.2.2')
    .replace(/^turns:127\.0\.0\.1/i, 'turns:10.0.2.2')
    .replace(/^turns:localhost/i, 'turns:10.0.2.2')
    .replace(/^turns:192\.168\.\d+\.\d+/i, 'turns:10.0.2.2');
}

function isTcpTurnUrl(url: string): boolean {
  return (
    (url.startsWith('turn:') || url.startsWith('turns:')) &&
    (url.includes('transport=tcp') || url.startsWith('turns:'))
  );
}

function isUdpTurnUrl(url: string): boolean {
  return url.startsWith('turn:') && !url.includes('transport=tcp');
}

/**
 * Dev emulator TURN topology:
 * - Both parent (emulator) and child (physical) reach coturn via adb reverse tcp:3478.
 * - Coturn allocates relay addresses as 192.168.x.x (LAN IP, since adb connects via loopback).
 * - Parent uses iceTransportPolicy:'relay' so ALL ICE traffic flows through coturn TCP — the
 *   emulator never routes directly to relay IPs.
 * - When parent tells coturn "forward to 192.168.x.x:child_relay_port", coturn recognises
 *   its own relay port and performs hairpin delivery to the child over the child's TCP connection.
 * - Rewriting 192.168.x.x to 10.0.2.2 breaks hairpin: coturn cannot route to 10.0.2.2 from the
 *   host machine, so ICE always fails. Keep child relay candidates unchanged.
 */
export function adaptRelayCandidateForEmulator(
  candidate: RTCIceCandidateInit,
  _apiBaseUrl: string,
): RTCIceCandidateInit {
  return candidate;
}

/**
 * Parent-on-emulator topology (local OR public Oracle TURN):
 * - Always drop UDP TURN — AVD NAT breaks UDP to LAN/public TURN hosts.
 * - Prefer TCP TURN (and turns).
 * - Local API only: rewrite loopback/LAN TURN host → 10.0.2.2 (+ adb reverse).
 * - Public Oracle TURN hosts are left as-is (reachable from the emulator).
 */
export function adaptIceServersForDev(
  servers: RTCIceServer[],
  apiBaseUrl: string,
): RTCIceServer[] {
  if (!(__DEV__ && isAndroidEmulator())) {
    return servers;
  }

  const rewriteHosts = isEmulatorLocalApi(apiBaseUrl);
  const expanded = expandIceServersByUrl(servers).map(server => {
    const url = normalizeUrls(server.urls as string | string[])[0];
    if (typeof url !== 'string') {
      return server;
    }
    return {
      ...server,
      urls: rewriteHosts ? rewriteTurnHostForEmulator(url) : url,
    };
  });

  const withoutUdpTurn = expanded.filter(server => {
    const url = normalizeUrls(server.urls as string | string[])[0];
    if (typeof url !== 'string') {
      return true;
    }
    if (isUdpTurnUrl(url)) {
      logIce('parent', 'dropping UDP TURN for emulator', url.slice(0, 48));
      return false;
    }
    return true;
  });

  const turnEntries = withoutUdpTurn.filter(server => {
    const url = normalizeUrls(server.urls as string | string[])[0];
    return typeof url === 'string' && (url.startsWith('turn') || url.startsWith('turns'));
  });
  const nonTurn = withoutUdpTurn.filter(server => {
    const url = normalizeUrls(server.urls as string | string[])[0];
    return typeof url === 'string' && !url.startsWith('turn') && !url.startsWith('turns');
  });

  if (turnEntries.length === 0) {
    logIce('parent', 'warning', 'no TCP TURN after emulator filter — ICE will fail');
    return withoutUdpTurn;
  }

  const tcpTurn = turnEntries.filter(server => {
    const url = normalizeUrls(server.urls as string | string[])[0];
    return typeof url === 'string' && isTcpTurnUrl(url);
  });

  const chosenTurn = tcpTurn.length > 0 ? tcpTurn : turnEntries;
  const result = [...nonTurn, ...chosenTurn];
  slog('ICE', 'emulator ICE adapt', {
    in: servers.length,
    out: result.length,
    tcpOnly: tcpTurn.length > 0,
    rewriteHosts,
    turnUrls: chosenTurn.map(s => normalizeUrls(s.urls as string | string[])[0]),
  });
  logIce(
    'parent',
    'emulator ICE servers',
    `${chosenTurn.length} turn + ${nonTurn.length} stun (tcp-only=${tcpTurn.length > 0})`,
  );
  return result;
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
  apiBaseUrl: string = config.apiBaseUrl,
): IceConfig {
  const iceServers = adaptIceServersForDev(mergeIceServers(apiServers), apiBaseUrl);
  if (!__DEV__ && !iceServers.some(hasTurnCredentials)) {
    logIce('config', 'warning', 'no ephemeral TURN credentials from API');
  }
  // Emulator ↔ physical (or emulator ↔ public TURN) needs relay; direct host paths fail.
  const forceRelay =
    usesLocalDevTurn(iceServers) ||
    (__DEV__ && isAndroidEmulator() && hasTurnServer(iceServers));
  return {
    iceServers,
    iceTransportPolicy: forceRelay ? 'relay' : 'all',
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
  console.log(`[webrtc:${label}] ${state}${suffix}`);
}

/** Backend already returns chronological order — do not reverse again. */
export function orderedSignalingMessages<T extends {id: string}>(messages: T[] = []): T[] {
  return [...messages];
}

export function describeIceFailure(
  iceState: string | undefined,
  remoteDesc: string | null | undefined,
  pendingCandidates: number,
): string {
  if (!remoteDesc) {
    return (
      'Live view didn’t start on the child phone. Confirm the child device is online and Clock Time permissions are finished, then try again.'
    );
  }
  if (pendingCandidates > 0) {
    return 'Connection couldn’t finish. Check Wi‑Fi or mobile data on both phones, then tap Reconnect.';
  }
  if (iceState === 'failed' || iceState === 'disconnected') {
    try {
      const host = new URL(config.apiBaseUrl).hostname;
      const localApi =
        host === '10.0.2.2' || host === '127.0.0.1' || host === 'localhost';
      if (localApi) {
        return (
          'Connection couldn’t be established in this development setup. ' +
          'Confirm the local network tools are running, then try again.'
        );
      }
    } catch {
      // fall through
    }
    return (
      'Connection couldn’t be established between the phones. ' +
      'Check Wi‑Fi or mobile data on both devices, then try again.'
    );
  }
  return 'Connection timed out. Check the network on both phones, then try again.';
}
