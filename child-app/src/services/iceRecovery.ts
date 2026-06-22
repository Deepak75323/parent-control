/**
 * P1-7: ICE restart and connectivity recovery (child publisher).
 */
import {RTCPeerConnection} from 'react-native-webrtc';
import {slog} from './flowLogger';

export type IceRecoveryCallbacks = {
  onRestartIce: () => Promise<void>;
  onRenegotiate: () => Promise<void>;
  onFatal: (reason: string) => void;
  reportEvent: (event: string, attrs?: Record<string, unknown>) => void;
};

const DISCONNECTED_GRACE_MS = 6000;
const RESTART_ICE_WAIT_MS = 10000;
const RENEGOTIATE_WAIT_MS = 15000;

export function attachIceRecovery(
  peer: RTCPeerConnection,
  callbacks: IceRecoveryCallbacks,
): () => void {
  let stage = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let disposed = false;
  /**
   * W2 fix: monotonic generation counter.
   * Incremented every time we reset back to "healthy" (connected/completed).
   * Any timer scheduled on generation N is a no-op if the current generation
   * has advanced past N — prevents stale timers from re-triggering recovery
   * after a rapid network flap (lose signal → regain → lose again in <1 s).
   */
  let generation = 0;

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  const schedule = (delayMs: number, gen: number, fn: () => void) => {
    clearTimer();
    timer = setTimeout(() => {
      timer = null;
      if (!disposed && generation === gen) fn();
    }, delayMs);
  };

  const onIceChange = () => {
    const state = peer.iceConnectionState ?? 'unknown';
    if (state === 'connected' || state === 'completed') {
      stage = 0;
      generation++; // invalidate any pending recovery timer for the old generation
      clearTimer();
      callbacks.reportEvent('ice_recovered', {state});
      return;
    }
    if (state !== 'disconnected' && state !== 'failed') {
      return;
    }
    // If a timer is already scheduled for this generation, do not schedule another.
    if (timer) {
      return;
    }
    const delay = state === 'failed' ? 4000 : DISCONNECTED_GRACE_MS;
    const myGen = generation; // capture before any async boundary
    schedule(delay, myGen, () => {
      const current = peer.iceConnectionState ?? 'unknown';
      if (current === 'connected' || current === 'completed') {
        return;
      }
      if (stage === 0) {
        stage = 1;
        callbacks.reportEvent('ice_restart_attempt', {state: current});
        try {
          if (typeof peer.restartIce === 'function') {
            peer.restartIce();
          }
        } catch (err) {
          slog('ICE', 'restartIce error', String(err));
        }
        void callbacks.onRestartIce();
        schedule(RESTART_ICE_WAIT_MS, myGen, () => {
          const after = peer.iceConnectionState ?? 'unknown';
          if (after === 'connected' || after === 'completed') {
            stage = 0;
            return;
          }
          stage = 2;
          callbacks.reportEvent('ice_renegotiate_attempt', {state: after});
          void callbacks.onRenegotiate();
          schedule(RENEGOTIATE_WAIT_MS, myGen, () => {
            const finalState = peer.iceConnectionState ?? 'unknown';
            if (finalState === 'connected' || finalState === 'completed') {
              stage = 0;
              return;
            }
            callbacks.reportEvent('ice_recovery_failed', {state: finalState});
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
