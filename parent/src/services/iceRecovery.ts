/**
 * ICE recovery for the parent viewer.
 *
 * On wifi↔cellular handoff the child sends an iceRestart offer. Parent must NOT
 * tear down and reconnect immediately — that answers a stale offer and races the
 * child's restart. Wait for the child's re-offer (handled by applyRemoteOffer);
 * only full-reconnect if ICE actually fails.
 */
import {RTCPeerConnection} from 'react-native-webrtc';
import {slog} from './streamDebug';

export type IceRecoveryCallbacks = {
  onRestartIce: () => Promise<void>;
  onRenegotiate: () => Promise<void>;
  onFatal: (reason: string) => void;
  reportEvent: (event: string, attrs?: Record<string, unknown>) => void;
};

/** Let child iceRestart offer arrive before we tear down the viewer PC. */
const DISCONNECTED_WAIT_FOR_CHILD_MS = 14000;
const FAILED_RECONNECT_DELAY_MS = 2500;
const RECONNECT_WAIT_MS = 20000;

export function attachIceRecovery(
  peer: RTCPeerConnection,
  callbacks: IceRecoveryCallbacks,
): () => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let disposed = false;
  let reconnectAttempted = false;

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

  const tryReconnect = async (state: string) => {
    if (reconnectAttempted) {
      callbacks.reportEvent('ice_recovery_failed', {state, stage: 2});
      callbacks.onFatal(`ICE_FAILED_${state}`);
      return;
    }
    reconnectAttempted = true;
    callbacks.reportEvent('ice_renegotiate_attempt', {state});
    slog('ICE', 'parent reconnect after ICE failed', {state});
    try {
      await callbacks.onRenegotiate();
    } catch (err) {
      slog('ICE', 'renegotiate failed', String(err));
      callbacks.reportEvent('ice_recovery_failed', {state, error: String(err)});
      callbacks.onFatal(`ICE_RECONNECT_FAILED_${state}`);
      return;
    }
    schedule(RECONNECT_WAIT_MS, () => {
      const finalState = peer.iceConnectionState ?? 'unknown';
      if (finalState === 'connected' || finalState === 'completed') {
        return;
      }
      callbacks.reportEvent('ice_recovery_failed', {state: finalState, stage: 2});
      callbacks.onFatal(`ICE_${finalState}`);
    });
  };

  const onIceChange = () => {
    const state = peer.iceConnectionState ?? 'unknown';
    if (state === 'connected' || state === 'completed') {
      reconnectAttempted = false;
      clearTimer();
      callbacks.reportEvent('ice_recovered', {state});
      return;
    }
    if (state !== 'disconnected' && state !== 'failed') {
      return;
    }
    if (timer) {
      return;
    }

    if (state === 'disconnected') {
      // Child should send iceRestart offer; applyRemoteOffer handles it.
      callbacks.reportEvent('ice_connection_lost', {
        state,
        nextRetryDelay: DISCONNECTED_WAIT_FOR_CHILD_MS,
        waitingForChildIceRestart: true,
      });
      schedule(DISCONNECTED_WAIT_FOR_CHILD_MS, () => {
        const current = peer.iceConnectionState ?? 'unknown';
        if (current === 'connected' || current === 'completed') {
          return;
        }
        void tryReconnect(current);
      });
      return;
    }

    // failed
    callbacks.reportEvent('ice_connection_lost', {
      state,
      nextRetryDelay: FAILED_RECONNECT_DELAY_MS,
    });
    schedule(FAILED_RECONNECT_DELAY_MS, () => {
      const current = peer.iceConnectionState ?? 'unknown';
      if (current === 'connected' || current === 'completed') {
        return;
      }
      void tryReconnect(current);
    });
  };

  peer.addEventListener('iceconnectionstatechange', onIceChange);

  return () => {
    disposed = true;
    clearTimer();
    peer.removeEventListener('iceconnectionstatechange', onIceChange);
  };
}
