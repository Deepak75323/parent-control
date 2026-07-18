import {
  adaptIceServersForDev,
  adaptRelayCandidateForEmulator,
  expandIceServersByUrl,
  describeIceFailure,
  defaultIceConfig,
} from '../webrtcIce';

declare const global: {__DEV__: boolean};

jest.mock('react-native', () => ({
  Platform: {
    OS: 'android',
    constants: {
      Fingerprint: 'generic/google/sdk_gphone64_arm64',
      Model: 'sdk_gphone64_arm64',
    },
  },
}));

jest.mock('../../utils/config', () => ({
  config: {
    apiBaseUrl: 'http://80.225.196.181',
    devIceServers: [],
  },
}));

jest.mock('../streamDebug', () => ({
  slog: jest.fn(),
}));

beforeAll(() => {
  global.__DEV__ = true;
});

describe('expandIceServersByUrl', () => {
  it('splits multi-URL TURN entry into one server per URL', () => {
    const expanded = expandIceServersByUrl([
      {
        urls: ['turn:192.168.1.2:3478', 'turn:192.168.1.2:3478?transport=tcp'],
        username: 'u',
        credential: 'c',
      },
    ]);
    expect(expanded).toHaveLength(2);
    expect(expanded[0].urls).toBe('turn:192.168.1.2:3478');
    expect(expanded[1].urls).toBe('turn:192.168.1.2:3478?transport=tcp');
    expect(expanded[0].username).toBe('u');
  });
});

describe('adaptIceServersForDev', () => {
  const emulatorApi = 'http://10.0.2.2:8080';

  it('drops UDP TURN and keeps TCP TURN for emulator', () => {
    const adapted = adaptIceServersForDev(
      [
        {urls: ['stun:stun.l.google.com:19302']},
        {
          urls: ['turn:192.168.1.2:3478', 'turn:192.168.1.2:3478?transport=tcp'],
          username: 'user',
          credential: 'pass',
        },
      ],
      emulatorApi,
    );
    const turnUrls = adapted
      .map(s => (Array.isArray(s.urls) ? s.urls[0] : s.urls))
      .filter(u => typeof u === 'string' && u.startsWith('turn'));
    expect(turnUrls).toEqual(['turn:10.0.2.2:3478?transport=tcp']);
  });

  it('keeps public Oracle TURN host but drops UDP on emulator', () => {
    const adapted = adaptIceServersForDev(
      [
        {
          urls: [
            'turn:80.225.196.181:3478',
            'turn:80.225.196.181:3478?transport=tcp',
          ],
          username: 'u',
          credential: 'c',
        },
      ],
      'http://80.225.196.181',
    );
    const turnUrls = adapted
      .map(s => (Array.isArray(s.urls) ? s.urls[0] : s.urls))
      .filter(u => typeof u === 'string' && u.startsWith('turn'));
    expect(turnUrls).toEqual(['turn:80.225.196.181:3478?transport=tcp']);
  });

  it('forces relay policy for emulator + public TURN', () => {
    const ice = defaultIceConfig(
      [
        {
          urls: ['turn:80.225.196.181:3478?transport=tcp'],
          username: 'u',
          credential: 'c',
        },
      ],
      undefined,
      'http://80.225.196.181',
    );
    expect(ice.iceTransportPolicy).toBe('relay');
  });
});

describe('adaptRelayCandidateForEmulator', () => {
  const emulatorApi = 'http://10.0.2.2:8080';

  it('keeps LAN relay candidates unchanged (coturn hairpin)', () => {
    const input = {
      candidate:
        'candidate:1 1 udp 41885439 192.168.1.2 49160 typ relay raddr 192.168.1.2 rport 49160',
    };
    expect(adaptRelayCandidateForEmulator(input, emulatorApi)).toEqual(input);
  });

  it('does not rewrite host candidates', () => {
    const input = {
      candidate: 'candidate:1 1 udp 2122260223 10.0.2.16 54321 typ host',
    };
    expect(adaptRelayCandidateForEmulator(input, emulatorApi)).toEqual(input);
  });
});

describe('describeIceFailure', () => {
  it('distinguishes missing offer from connection failure', () => {
    expect(describeIceFailure('checking', null, 0)).toMatch(/didn.?t start/i);
    expect(describeIceFailure('failed', 'offer', 0)).toMatch(/Connection couldn.?t be established/i);
  });

  it('does not expose infrastructure jargon for public API hosts', () => {
    const msg = describeIceFailure('failed', 'offer', 0);
    expect(msg).not.toMatch(/dev-up\.sh|\bTURN\b|\bICE\b|coturn/i);
    expect(msg).toMatch(/try again/i);
  });
});
