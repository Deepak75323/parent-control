import {Client} from '@stomp/stompjs';
import {
  subscribeSignalingRoom,
  resubscribeSignalingRooms,
  unsubscribeSignalingRoom,
  clearSignalingSubscriptions,
} from '../services/signalingWebSocket';

jest.mock('../services/websocketService', () => ({
  getWebSocketClient: jest.fn(),
  connectWebSocket: jest.fn(),
}));

jest.mock('../services/streamDebug', () => ({
  slog: jest.fn(),
}));

const {getWebSocketClient} = jest.requireMock('../services/websocketService');

describe('signalingWebSocket', () => {
  beforeEach(() => {
    clearSignalingSubscriptions();
    jest.clearAllMocks();
  });

  it('resubscribes all signaling rooms after WS reconnect', () => {
    const handler = jest.fn();
    subscribeSignalingRoom('screen_device_session', handler);

    const subscribe = jest.fn((dest: string, cb: (msg: {body: string}) => void) => {
      cb({body: JSON.stringify({messageType: 'offer', senderRole: 'CHILD'})});
      return {unsubscribe: jest.fn()};
    });
    const client = {connected: true, subscribe} as unknown as Client;

    resubscribeSignalingRooms(client);

    expect(subscribe).toHaveBeenCalledWith(
      '/topic/signaling/screen_device_session',
      expect.any(Function),
    );
    expect(handler).toHaveBeenCalledWith(
      expect.objectContaining({messageType: 'offer', senderRole: 'CHILD'}),
    );
  });

  it('queues subscription when client is disconnected', () => {
    getWebSocketClient.mockReturnValue(null);
    const handler = jest.fn();
    subscribeSignalingRoom('audio_room', handler);
    expect(getWebSocketClient).toHaveBeenCalled();
  });

  it('unsubscribes and clears handler map', () => {
    const unsub = jest.fn();
    getWebSocketClient.mockReturnValue({
      connected: true,
      subscribe: () => ({unsubscribe: unsub}),
    });
    const handler = jest.fn();
    subscribeSignalingRoom('camera_room', handler);
    unsubscribeSignalingRoom('camera_room');
    expect(unsub).toHaveBeenCalled();
    clearSignalingSubscriptions();
  });
});
