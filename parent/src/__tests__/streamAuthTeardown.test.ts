jest.mock('../services/api', () => ({
  monitoringApi: {
    stopScreenShare: jest.fn(() => Promise.resolve()),
  },
}));

jest.mock('../services/webrtcService', () => ({
  disconnectViewer: jest.fn(() => Promise.resolve()),
}));

import {monitoringApi} from '../services/api';
import {disconnectViewer} from '../services/webrtcService';
import {
  beginSession,
  endSession,
  clearAllStreamListeners,
  clearStickySessionId,
  getSessionIdForBackendStop,
} from '../services/streamSessionOwner';
import {
  stopBackendStreamSession,
  teardownActiveStreamOnAuthEnd,
} from '../services/streamAuthTeardown';

describe('teardownActiveStreamOnAuthEnd', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    endSession();
    clearStickySessionId();
    clearAllStreamListeners();
  });

  it('stops backend session and disconnects viewer when session is active', async () => {
    beginSession('camera_room', 'sess-123');
    await teardownActiveStreamOnAuthEnd();
    expect(monitoringApi.stopScreenShare).toHaveBeenCalledWith('sess-123');
    expect(disconnectViewer).toHaveBeenCalled();
  });

  it('still stops backend after local endSession cleared live ids', async () => {
    beginSession('camera_room', 'sess-sticky');
    endSession();
    expect(getSessionIdForBackendStop()).toBe('sess-sticky');
    await teardownActiveStreamOnAuthEnd();
    expect(monitoringApi.stopScreenShare).toHaveBeenCalledWith('sess-sticky');
    expect(disconnectViewer).toHaveBeenCalled();
  });

  it('still disconnects when there is no session id', async () => {
    beginSession('camera_room', undefined);
    await teardownActiveStreamOnAuthEnd();
    expect(monitoringApi.stopScreenShare).not.toHaveBeenCalled();
    expect(disconnectViewer).toHaveBeenCalled();
  });
});

describe('stopBackendStreamSession', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    endSession();
    clearStickySessionId();
    clearAllStreamListeners();
  });

  it('uses sticky session id after disconnect cleared live state', async () => {
    beginSession('room', 'sess-unmount');
    endSession();
    const ok = await stopBackendStreamSession();
    expect(ok).toBe(true);
    expect(monitoringApi.stopScreenShare).toHaveBeenCalledWith('sess-unmount');
    expect(getSessionIdForBackendStop()).toBeUndefined();
  });
});
