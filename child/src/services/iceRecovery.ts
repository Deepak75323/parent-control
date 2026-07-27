/**
 * ICE restart / connectivity recovery for the child publisher.
 *
 * Wi‑Fi ↔ cellular handoffs leave host candidates on the old interface. Calling
 * peer.restartIce() alone is not enough — we must send a new offer with
 * iceRestart:true so both sides gather fresh candidates (usually via TURN).
 */
import {RTCPeerConnection} from 'react-native-webrtc';
import {slog} from './flowLogger';

export type IceRecoveryCallbacks = {
  /** Send createOffer({iceRestart:true}) + signaling. */
  onRestartIce: () => Promise<void>;
  onRenegotiate: () => Promise<void>;
  onFatal: (reason: string) => void;
  reportEvent: (event: string, attrs?: Record<string, unknown>) => void;
};

/** Brief grace so a 1s radio flap can self-heal before we renegotiate. */
const DISCONNECTED_GRACE_MS = 2000;
/** Wait for ICE to come back after an iceRestart offer. */
const ICE_RESTART_WAIT_MS = 12000;
const MAX_RECOVERY_ATTEMPTS = 2;

export function attachIceRecovery(
  peer: RTCPeerConnection,
  callbacks: IceRecoveryCallbacks,
): () => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let disposed = false;
  let recoveryAttempts = 0;
  let lastSuccessfulConnection = Date.now();
  let generation = 0;
  let iceRestartInFlight = false;

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

  const attemptIceRestartOffer = async (gen: number, fromState: string) => {
    if (iceRestartInFlight) {
      return;
    }
    iceRestartInFlight = true;
    recoveryAttempts++;
    callbacks.reportEvent('ice_restart_attempt', {
      state: fromState,
      attempt: recoveryAttempts,
      connectionAge: Date.now() - lastSuccessfulConnection,
    });
    slog('ICE', 'sending iceRestart offer', {attempt: recoveryAttempts, state: fromState});
    try {
      // Prefer full SDP ice restart; restartIce() alone does not cross interfaces.
      await callbacks.onRestartIce();
    } catch (err) {
      slog('ICE', 'iceRestart offer failed', String(err));
      callbacks.reportEvent('ice_restart_error', {error: String(err), attempt: recoveryAttempts});
    } finally {
      iceRestartInFlight = false;
    }

    schedule(ICE_RESTART_WAIT_MS, gen, () => {
      const after = peer.iceConnectionState ?? 'unknown';
      if (after === 'connected' || after === 'completed') {
        return;
      }
      if (recoveryAttempts < MAX_RECOVERY_ATTEMPTS) {
        callbacks.reportEvent('ice_renegotiate_attempt', {
          state: after,
          attempt: recoveryAttempts,
        });
        void callbacks.onRenegotiate().then(() => {
          schedule(ICE_RESTART_WAIT_MS, gen, () => {
            const finalState = peer.iceConnectionState ?? 'unknown';
            if (finalState === 'connected' || finalState === 'completed') {
              return;
            }
            callbacks.reportEvent('ice_recovery_failed', {
              state: finalState,
              attempt: recoveryAttempts,
            });
            callbacks.onFatal(`ICE_FINAL_FAILURE_${finalState}`);
          });
        });
        return;
      }
      callbacks.reportEvent('ice_recovery_failed', {
        state: after,
        attempt: recoveryAttempts,
      });
      callbacks.onFatal(`ICE_FAILED_${after}`);
    });
  };

  const onIceChange = () => {
    const state = peer.iceConnectionState ?? 'unknown';

    if (state === 'connected' || state === 'completed') {
      generation++;
      recoveryAttempts = 0;
      iceRestartInFlight = false;
      lastSuccessfulConnection = Date.now();
      clearTimer();
      callbacks.reportEvent('ice_recovered', {state});
      return;
    }

    if (state !== 'disconnected' && state !== 'failed') {
      return;
    }

    const myGen = generation;

    // Interface change often jumps disconnected → failed. Prefer one iceRestart
    // offer while network may still be up, instead of tearing down immediately.
    if (state === 'failed') {
      clearTimer();
      if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
        callbacks.reportEvent('ice_connection_lost', {
          state,
          recoveryAttempts,
          fastFail: true,
        });
        schedule(1500, myGen, () => {
          const current = peer.iceConnectionState ?? 'unknown';
          if (current === 'connected' || current === 'completed') {
            return;
          }
          callbacks.onFatal(`ICE_FAILED_${current}`);
        });
        return;
      }
      callbacks.reportEvent('ice_connection_lost', {
        state,
        recoveryAttempts,
        nextRetryDelay: 500,
        iceRestartOffer: true,
      });
      schedule(500, myGen, () => {
        const current = peer.iceConnectionState ?? 'unknown';
        if (current === 'connected' || current === 'completed') {
          return;
        }
        void attemptIceRestartOffer(myGen, current);
      });
      return;
    }

    // disconnected
    if (timer || iceRestartInFlight) {
      return;
    }
    callbacks.reportEvent('ice_connection_lost', {
      state,
      recoveryAttempts,
      nextRetryDelay: DISCONNECTED_GRACE_MS,
    });
    schedule(DISCONNECTED_GRACE_MS, myGen, () => {
      const current = peer.iceConnectionState ?? 'unknown';
      if (current === 'connected' || current === 'completed') {
        return;
      }
      void attemptIceRestartOffer(myGen, current);
    });
  };

  peer.addEventListener('iceconnectionstatechange', onIceChange);

  return () => {
    disposed = true;
    clearTimer();
    peer.removeEventListener('iceconnectionstatechange', onIceChange);
  };
}
