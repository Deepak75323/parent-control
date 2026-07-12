/**
 * P1-7: ICE restart and connectivity recovery controller.
 */
import {RTCPeerConnection} from 'react-native-webrtc';
import {slog} from './streamDebug';

export type IceRecoveryCallbacks = {
  onRestartIce: () => Promise<void>;
  onRenegotiate: () => Promise<void>;
  onFatal: (reason: string) => void;
  reportEvent: (event: string, attrs?: Record<string, unknown>) => void;
};

const DISCONNECTED_GRACE_MS = 5000;
const RESTART_ICE_WAIT_MS = 8000;
const RENEGOTIATE_WAIT_MS = 12000;

export function attachIceRecovery(
  peer: RTCPeerConnection,
  callbacks: IceRecoveryCallbacks,
): () => void {
  let stage = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let disposed = false;

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  const schedule = (delayMs: number, fn: () => void) => {
    clearTimer();
    timer = setTimeout(() => {
      timer = null;
      if (!disposed) fn();
    }, delayMs);
  };

  const onIceChange = () => {
    const state = peer.iceConnectionState ?? 'unknown';
    if (state === 'connected' || state === 'completed') {
      stage = 0;
      clearTimer();
      callbacks.reportEvent('ice_recovered', {state, stage});
      return;
    }
    if (state !== 'disconnected' && state !== 'failed') {
      return;
    }
    if (timer) {
      return;
    }
    const delay = state === 'failed' ? 3000 : DISCONNECTED_GRACE_MS;
    schedule(delay, async () => {
      const current = peer.iceConnectionState ?? 'unknown';
      if (current === 'connected' || current === 'completed') {
        return;
      }
      if (stage === 0) {
        stage = 1;
        callbacks.reportEvent('ice_restart_attempt', {state: current});
        slog('ICE', 'attempting restartIce', {state: current});
        try {
          if (typeof peer.restartIce === 'function') {
            peer.restartIce();
          }
          await callbacks.onRestartIce();
        } catch (err) {
          slog('ICE', 'restartIce failed', String(err));
        }
        schedule(RESTART_ICE_WAIT_MS, async () => {
          const after = peer.iceConnectionState ?? 'unknown';
          if (after === 'connected' || after === 'completed') {
            stage = 0;
            return;
          }
          stage = 2;
          callbacks.reportEvent('ice_renegotiate_attempt', {state: after});
          try {
            await callbacks.onRenegotiate();
          } catch (err) {
            slog('ICE', 'renegotiate failed', String(err));
          }
          schedule(RENEGOTIATE_WAIT_MS, () => {
            const finalState = peer.iceConnectionState ?? 'unknown';
            if (finalState === 'connected' || finalState === 'completed') {
              stage = 0;
              return;
            }
            callbacks.reportEvent('ice_recovery_failed', {state: finalState, stage});
            callbacks.onFatal(`ICE_${finalState}`);
          });
        });
      }
    });
  };

  peer.addEventListener('iceconnectionstatechange', onIceChange);

  return () => {
    disposed = true;
    clearTimer();
    peer.removeEventListener('iceconnectionstatechange', onIceChange);
  };
}
