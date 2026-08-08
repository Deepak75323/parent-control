jest.mock('../services/api', () => ({
  deviceApi: {
    wake: jest.fn(),
    get: jest.fn(),
  },
}));

import {deviceApi} from '../services/api';
import {waitForDevicePresence} from '../utils/waitForDevicePresence';

const mockedWake = deviceApi.wake as jest.MockedFunction<typeof deviceApi.wake>;
const mockedGet = deviceApi.get as jest.MockedFunction<typeof deviceApi.get>;

function buildDeviceResponse(overrides: Record<string, unknown> = {}) {
  return {
    data: {
      success: true,
      data: {
        id: 'device-1',
        deviceName: 'Xiaomi',
        status: 'OFFLINE',
        ...overrides,
      },
    },
  } as unknown as ReturnType<typeof deviceApi.get> extends Promise<infer R> ? R : never;
}

function buildWakeResponse(fcmDispatched = true) {
  return {
    data: {
      success: true,
      data: {
        deviceId: 'device-1',
        fcmDispatched,
        status: 'OFFLINE',
        lastSeenAt: null,
      },
    },
  } as unknown as ReturnType<typeof deviceApi.wake> extends Promise<infer R> ? R : never;
}

beforeEach(() => {
  mockedWake.mockReset();
  mockedGet.mockReset();
});

describe('waitForDevicePresence', () => {
  it('dispatches wake and resolves as soon as device reports fresh lastSeenAt', async () => {
    let clock = 1_000_000;
    const now = () => clock;
    const sleep = jest.fn(async (ms: number) => {
      clock += ms;
    });

    mockedWake.mockResolvedValue(buildWakeResponse(true));
    // First poll: stale. Second poll: fresh.
    mockedGet
      .mockResolvedValueOnce(
        buildDeviceResponse({
          status: 'OFFLINE',
          lastSeenAt: new Date(clock - 60 * 60 * 1000).toISOString(),
        }),
      )
      .mockResolvedValueOnce(
        buildDeviceResponse({
          status: 'ONLINE',
          lastSeenAt: new Date(clock).toISOString(),
        }),
      );

    const result = await waitForDevicePresence('device-1', {
      timeoutMs: 8000,
      pollIntervalMs: 1500,
      now,
      sleep,
    });

    expect(mockedWake).toHaveBeenCalledTimes(1);
    expect(mockedGet).toHaveBeenCalledTimes(2);
    expect(result.present).toBe(true);
    expect(result.attempts).toBe(2);
    expect(result.wakeDispatched).toBe(true);
    expect(result.device?.status).toBe('ONLINE');
    expect(sleep).toHaveBeenCalledWith(1500);
  });

  it('returns present=false after the timeout when device never comes back', async () => {
    let clock = 5_000_000;
    const now = () => clock;
    const sleep = jest.fn(async (ms: number) => {
      clock += ms;
    });

    mockedWake.mockResolvedValue(buildWakeResponse(false));
    mockedGet.mockResolvedValue(
      buildDeviceResponse({
        status: 'OFFLINE',
        lastSeenAt: new Date(clock - 60 * 60 * 1000).toISOString(),
      }),
    );

    const result = await waitForDevicePresence('device-1', {
      timeoutMs: 6000,
      pollIntervalMs: 2000,
      now,
      sleep,
    });

    expect(result.present).toBe(false);
    expect(result.wakeDispatched).toBe(false);
    // With a 6s budget and 2s poll interval we expect exactly 3 attempts before we
    // stop scheduling further sleeps.
    expect(result.attempts).toBe(3);
    expect(mockedGet).toHaveBeenCalledTimes(3);
  });

  it('tolerates a failing wake endpoint and still polls', async () => {
    let clock = 0;
    const now = () => clock;
    const sleep = jest.fn(async (ms: number) => {
      clock += ms;
    });

    mockedWake.mockRejectedValue(new Error('404 wake not deployed'));
    mockedGet.mockResolvedValueOnce(
      buildDeviceResponse({
        status: 'ONLINE',
        lastSeenAt: new Date(clock).toISOString(),
      }),
    );

    const result = await waitForDevicePresence('device-1', {
      timeoutMs: 4000,
      pollIntervalMs: 1000,
      now,
      sleep,
    });

    expect(result.present).toBe(true);
    expect(result.wakeDispatched).toBe(false);
  });

  it('skips wake when wake option is false', async () => {
    let clock = 0;
    const now = () => clock;
    const sleep = jest.fn(async (ms: number) => {
      clock += ms;
    });

    mockedGet.mockResolvedValueOnce(
      buildDeviceResponse({
        status: 'ONLINE',
        lastSeenAt: new Date(clock).toISOString(),
      }),
    );

    const result = await waitForDevicePresence('device-1', {
      timeoutMs: 4000,
      pollIntervalMs: 1000,
      wake: false,
      now,
      sleep,
    });

    expect(mockedWake).not.toHaveBeenCalled();
    expect(result.present).toBe(true);
  });
});
