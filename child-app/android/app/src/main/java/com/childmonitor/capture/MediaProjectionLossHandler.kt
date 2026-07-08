package com.childmonitor.capture

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.sync.HeartbeatSyncManager
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry

/**
 * Detects MediaProjection consent loss (process death or [MediaProjection.onStop]),
 * pushes an immediate capability heartbeat, and triggers Android-compliant re-consent.
 *
 * Does not persist binder tokens — only reports runtime availability to the server.
 */
object MediaProjectionLossHandler {

    private const val TAG = "MediaProjectionLoss"
    private const val DEBOUNCE_MS = 5_000L

    @Volatile
    private var lastHandledAtMs = 0L

    @Volatile
    private var lastHandledState: MediaProjectionConsentState? = null

    /**
     * True when the child previously granted screen capture but capture is unavailable now.
     */
    fun isProjectionCapabilityLost(context: Context): Boolean {
        val state = MediaProjectionTokenStore.getConsentState(context)
        return state.blocksScreenShare() &&
            state != MediaProjectionConsentState.NEVER_GRANTED &&
            state != MediaProjectionConsentState.RECOVERY_PENDING
    }

    fun isScreenShareAvailable(context: Context): Boolean =
        MediaProjectionTokenStore.hasValidToken(context)

    fun currentConsentState(context: Context): MediaProjectionConsentState =
        MediaProjectionTokenStore.getConsentState(context)

    /**
     * Report loss to server and attempt silent re-consent when capability is unavailable.
     */
    fun onProjectionUnavailable(context: Context, reason: String) {
        if (!MonitoringPrefs.isPaired(context)) return
        if (MonitoringPrefs.isPermissionSetupInProgress(context)) return
        if (isScreenShareAvailable(context)) return

        val state = MediaProjectionTokenStore.getConsentState(context)
        if (state == MediaProjectionConsentState.NEVER_GRANTED) return
        if (state == MediaProjectionConsentState.RECOVERY_PENDING) return

        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastHandledAtMs < DEBOUNCE_MS && lastHandledState == state) {
                Log.d(TAG, "debounced projection loss reason=$reason state=$state")
                return
            }
            lastHandledAtMs = now
            lastHandledState = state
        }

        if (state == MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH) {
            MediaProjectionTokenStore.logProjectionLostAfterProcessRestart(context)
        }

        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection capability lost",
            "reason=$reason state=$state",
        )
        Log.w(TAG, "Projection capability lost — heartbeat + recovery reason=$reason state=$state")

        val app = context.applicationContext
        Thread {
            HeartbeatSyncManager.syncProjectionLost(app, reason)
            MediaProjectionRecoveryManager.attemptRecovery(app, reason)
        }.start()
    }

    /** Push updated capability flags after fresh user/system consent. */
    fun onProjectionRestored(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) return
        if (!isScreenShareAvailable(context)) return

        lastHandledState = MediaProjectionConsentState.GRANTED_ACTIVE
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection capability restored — syncing heartbeat",
        )
        val app = context.applicationContext
        Thread {
            HeartbeatSyncManager.syncProjectionRestored(app)
        }.start()
    }

    /** @visibleForTesting */
    internal fun resetForTests() {
        lastHandledAtMs = 0L
        lastHandledState = null
    }
}
