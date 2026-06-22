import {deviceApi} from './api';
import {getDeviceToken} from './deviceStorage';

const INTERVAL_MS = 60_000;

let timer: ReturnType<typeof setInterval> | null = null;

export async function startHeartbeat(): Promise<void> {
  const token = await getDeviceToken();
  if (!token) return;

  const send = () => {
    deviceApi.heartbeat({
      batteryLevel: 100,
      charging: false,
      networkType: 'WIFI',
    }).catch(() => {});
  };

  send();
  if (timer) clearInterval(timer);
  timer = setInterval(send, INTERVAL_MS);
}

export function stopHeartbeat(): void {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}
