/**
 * @deprecated Device STOMP is owned exclusively by native Android (P1-6).
 * This module re-exports the signaling bridge for backward compatibility.
 */
export {
  ensureDeviceWebSocket,
  subscribeSignalingRoom,
  unsubscribeSignalingRoom,
  subscribeStreamRequests,
  subscribeStreamStop,
  subscribeStreamControl,
  subscribeDeviceCommands,
  pauseStreamWebSocketSubscriptions,
  disconnectDeviceWebSocket,
  getStompOwnershipHealth,
} from './stompBridge';
