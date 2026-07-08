import {
  adaptIceServersForDev,
  adaptRelayCandidateForEmulator,
  expandIceServersByUrl,
  describeIceFailure,
} from '../webrtcIce';

declare const global: {__DEV__: boolean};

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

  it('leaves config unchanged for physical device API host', () => {
    const servers = [
      {
        urls: ['turn:192.168.1.2:3478', 'turn:192.168.1.2:3478?transport=tcp'],
        username: 'u',
        credential: 'c',
      },
    ];
    expect(adaptIceServersForDev(servers, 'http://192.168.1.2:8080')).toEqual(servers);
  });
});

describe('adaptRelayCandidateForEmulator', () => {
  const emulatorApi = 'http://10.0.2.2:8080';

  it('rewrites LAN relay addresses to 10.0.2.2', () => {
    const input = {
      candidate:
        'candidate:1 1 udp 41885439 192.168.1.2 49160 typ relay raddr 192.168.1.2 rport 49160',
    };
    const out = adaptRelayCandidateForEmulator(input, emulatorApi);
    expect(out.candidate).toContain('10.0.2.2');
    expect(out.candidate).not.toContain('192.168.1.2');
  });

  it('does not rewrite host candidates', () => {
    const input = {
      candidate: 'candidate:1 1 udp 2122260223 10.0.2.16 54321 typ host',
    };
    expect(adaptRelayCandidateForEmulator(input, emulatorApi)).toEqual(input);
  });
});

describe('describeIceFailure', () => {
  it('distinguishes missing offer from ICE failure', () => {
    expect(describeIceFailure('checking', null, 0)).toMatch(/did not start streaming/i);
    expect(describeIceFailure('failed', 'offer', 0)).toMatch(/ICE could not connect/i);
  });
});
