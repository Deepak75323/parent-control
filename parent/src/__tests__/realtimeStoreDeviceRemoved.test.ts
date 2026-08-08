import {useRealtimeStore} from '../store/realtimeStore';

describe('notifyDeviceRemoved', () => {
  beforeEach(() => {
    useRealtimeStore.getState().reset();
  });

  it('drops the device from live status and bumps the device list version', () => {
    const deviceId = 'device-a';
    useRealtimeStore.getState().upsertDeviceStatus({
      deviceId,
      status: 'ONLINE',
      batteryLevel: 80,
    });
    useRealtimeStore.getState().setLiveLocation(deviceId, {
      id: 'loc-1',
      latitude: 1,
      longitude: 2,
      recordedAt: new Date().toISOString(),
    });
    const versionBefore = useRealtimeStore.getState().deviceListVersion;

    useRealtimeStore.getState().notifyDeviceRemoved(deviceId);

    const state = useRealtimeStore.getState();
    expect(state.deviceStatuses[deviceId]).toBeUndefined();
    expect(state.liveLocations[deviceId]).toBeUndefined();
    expect(state.deviceListVersion).toBe(versionBefore + 1);
  });

  it('does not clear an active pairing code on the previous parent', () => {
    useRealtimeStore.getState().setPendingPairingCode('ABC123', [], null);
    useRealtimeStore.getState().notifyDeviceRemoved('other-device');

    expect(useRealtimeStore.getState().pendingPairingCode).toBe('ABC123');
  });
});
