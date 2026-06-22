/**
 * Headless JS task — runs when native FGS receives a camera/audio/RN-screen stream
 * request and the main RN UI process is not active.
 */
import {NativeEventEmitter, NativeModules, Platform} from 'react-native';
import {switchCamera} from '../services/webrtcService';
import {
  processHeadlessStreamRequest,
  startStreamingListener,
  stopStreamingListener,
} from '../services/streamingService';
import {nativeModules} from '../nativeModules';
import {slog} from '../services/streamDebug';

type HeadlessPayload = {
  roomId?: string;
  sessionId?: string;
  streamType?: string;
  cameraFacing?: string;
  action?: string;
};

let keepAlive = true;

export function signalHeadlessStreamStop(): void {
  keepAlive = false;
}

export default async function streamingHeadlessTask(data: HeadlessPayload): Promise<void> {
  if (data.action === 'switch_camera') {
    await switchCamera().catch(err => slog('HEADLESS', 'switch camera failed', String(err)));
    return;
  }

  slog('HEADLESS', 'task started', data);
  keepAlive = true;
  await nativeModules.setJsStreamingListenerActive(true);

  let controlSub: {remove: () => void} | null = null;
  if (Platform.OS === 'android') {
    const emitter = new NativeEventEmitter(NativeModules.ChildMonitorNative);
    controlSub = emitter.addListener('nativeStreamControl', (event: {command?: string}) => {
      if (event.command === 'SWITCH_CAMERA') {
        switchCamera().catch(err => slog('HEADLESS', 'switch failed', String(err)));
      } else if (event.command === 'STOP') {
        keepAlive = false;
      }
    });
  }

  try {
    startStreamingListener();
    if (data.roomId && data.sessionId && data.streamType) {
      processHeadlessStreamRequest(data);
    }
    // Stay alive until native coordinator stops the headless service.
    await new Promise<void>(resolve => {
      const check = () => {
        if (!keepAlive) {
          resolve();
          return;
        }
        setTimeout(check, 2000);
      };
      check();
    });
  } finally {
    controlSub?.remove();
    stopStreamingListener();
    await nativeModules.setJsStreamingListenerActive(false);
    slog('HEADLESS', 'task finished');
  }
}
