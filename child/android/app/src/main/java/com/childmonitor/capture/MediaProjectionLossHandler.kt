package com.childmonitor.capture

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.oem.OemVendor
import com.childmonitor.sync.HeartbeatSyncManager
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry

/**
 * Detects MediaProjection consent loss and notifies the parent.
 * Does **not** launch Cast — re-consent only via setup or parent Start Screen.
 */
object MediaProjectionLossHandler {

    private const val TAG = "MediaProjectionLoss"
    private const val DEBOUNCE_MS = 5_000L

    @Volatile
    private var lastHandledAtMs = 0L

    @Volatile
    private var lastHandledState: MediaProjectionConsentState? = null

    // ─── State queries ────────────────────────────────────────────────────────

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

    // ─── Loss events ─────────────────────────────────────────────────────────

    /**
     * Main loss handler — report loss to server and mark LOST (no Cast launch).
     *
     * Debounced: duplicate calls within [DEBOUNCE_MS] for the same state are suppressed to
     * avoid thundering-herd heartbeats when multiple components detect the same loss event.
     *
     * This method is safe to call from any thread.
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

        val oem = OemVendor.detect()
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection capability lost",
            "reason=$reason state=$state oem=${oem.id}",
        )
        Log.w(TAG, "Projection capability lost — heartbeat + mark LOST reason=$reason state=$state oem=${oem.id}")

        MediaProjectionRecoveryTelemetry.recordLoss(context, reason, state.name)

        val app = context.applicationContext
        Thread {
            HeartbeatSyncManager.syncProjectionLost(app, reason)
            MediaProjectionRecoveryManager.attemptRecovery(app, reason)
        }.start()
    }

    /**
     * Called from [BootReceiver] when the device has rebooted and the stored consent state
     * indicates the binder token was lost.
     *
     * On boot the app process is fresh — there is no in-memory projection — so the state
     * is always [MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH] if consent was
     * previously granted. Reports loss to the parent; does not open Cast.
     */
    fun onProjectionLostOnBoot(context: Context, consentState: MediaProjectionConsentState) {
        if (!MonitoringPrefs.isPaired(context)) return
        if (consentState == MediaProjectionConsentState.GRANTED_ACTIVE ||
            consentState == MediaProjectionConsentState.NEVER_GRANTED
        ) return

        val oem = OemVendor.detect()
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection lost on boot",
            "state=${consentState.name} oem=${oem.id}",
        )
        Log.w(TAG, "Projection binder lost on boot — mark LOST only state=${consentState.name} oem=${oem.id}")

        MediaProjectionRecoveryTelemetry.recordBootWithLostConsent(context, consentState.name)

        // Detect + notify only — never schedule WorkManager that used to open Cast.
        Thread {
            HeartbeatSyncManager.syncProjectionLost(context.applicationContext, "boot_projection_lost")
        }.start()
        if (consentState != MediaProjectionConsentState.REQUIRES_RECONSENT &&
            consentState != MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH
        ) {
            MediaProjectionTokenStore.markRequiresReconsent(context, "boot_projection_lost")
        }
        MediaProjectionRecoveryWorker.cancel(context)
    }

    /**
     * Called when the OS signals low memory ([Service.onLowMemory]). The process may be
     * killed shortly after. Records telemetry before potential death; the state will be
     * correctly reconciled to [MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH] on
     * next start via [MediaProjectionTokenStore.getConsentState].
     */
    fun onLowMemoryWarning(context: Context) {
        val hasProjection = ScreenCaptureController.activeProjection() != null
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "onLowMemory — projection binder at risk",
            "activeProjection=$hasProjection",
        )
        Log.w(TAG, "onLowMemory — if process is killed, binder will be lost (activeProjection=$hasProjection)")
        // Use commit() not apply() — we need the write to complete before the process dies.
        MediaProjectionRecoveryTelemetry.recordLowMemoryWarning(context, hasProjection)
    }

    /**
     * Called when the user swipes the app away from Recents ([Service.onTaskRemoved]).
     * On many OEMs this causes the process to be killed shortly after, invalidating the
     * projection binder. Records telemetry; recovery is handled on next process start.
     */
    fun onTaskRemovedWarning(context: Context) {
        val hasProjection = ScreenCaptureController.activeProjection() != null
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "onTaskRemoved — process may be killed by OEM",
            "activeProjection=$hasProjection",
        )
        Log.w(TAG, "onTaskRemoved — projection binder may be lost (activeProjection=$hasProjection)")
        MediaProjectionRecoveryTelemetry.recordTaskRemoved(context, hasProjection)
    }

    // ─── Restore ──────────────────────────────────────────────────────────────

    /** Push updated capability flags after fresh user/system consent. */
    fun onProjectionRestored(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) return
        if (!isScreenShareAvailable(context)) return

        lastHandledState = MediaProjectionConsentState.GRANTED_ACTIVE
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection capability restored — syncing heartbeat",
        )
        MediaProjectionRecoveryTelemetry.recordRestored(context)
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
