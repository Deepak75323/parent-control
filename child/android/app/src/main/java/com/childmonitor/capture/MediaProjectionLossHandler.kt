package com.childmonitor.capture

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import com.childmonitor.MonitoringPrefs
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.oem.OemVendor
import com.childmonitor.sync.HeartbeatSyncManager
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry

/**
 * Detects MediaProjection consent loss (process death, [MediaProjection.onStop], low-memory kill,
 * task removal), pushes an immediate capability heartbeat, and triggers Android-compliant
 * re-consent.
 *
 * ## Detection sources
 *
 * | Source | Trigger |
 * |--------|---------|
 * | Process death (LMKD) | Detected on next launch via `getConsentState()` returning `LOST_AFTER_PROCESS_DEATH` |
 * | Reboot | Detected in `BootReceiver` — calls `onProjectionLostOnBoot()` |
 * | [MediaProjection.onStop] | `ScreenCaptureController` callback → `onProjectionUnavailable()` |
 * | Low-memory warning | `ScreenCaptureForegroundService.onLowMemory()` → `onLowMemoryWarning()` |
 * | Task removal | `ScreenCaptureForegroundService.onTaskRemoved()` → `onTaskRemovedWarning()` |
 * | `loadIntent()` null | `MediaProjectionTokenStore.loadIntent()` → `onProjectionUnavailable()` |
 *
 * ## OEM notes
 *
 * - **MIUI/HyperOS**: Process death is more aggressive. Battery optimization exemption
 *   reduces LMKD frequency. BAL restrictions mean recovery must go through
 *   [BalSafeActivityLauncher] (already used by [MediaProjectionRecoveryManager]).
 * - **Samsung One UI**: No special loss handling needed beyond the standard path.
 * - **Android 14+ (API 34)**: [MediaProjection.onStop] fires on the binder thread, not the
 *   main thread. The 5 s debounce window handles rapid consecutive callbacks correctly.
 * - **Android 15 (API 35)**: FGS timeout is a separate signal handled by [TypedStreamFgsTimeout];
 *   it triggers its own teardown before calling `stopSelf()`. Loss is detected normally.
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
     * Main loss handler — report loss to server and attempt silent re-consent.
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
        Log.w(TAG, "Projection capability lost — heartbeat + recovery reason=$reason state=$state oem=${oem.id}")

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
     * previously granted. This method reports the loss and schedules a WorkManager recovery
     * job with a short initial delay (to give the app a chance to complete boot setup first).
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
        Log.w(TAG, "Projection binder lost on boot — scheduling recovery state=${consentState.name} oem=${oem.id}")

        MediaProjectionRecoveryTelemetry.recordBootWithLostConsent(context, consentState.name)

        // The heartbeat already runs via BootWakeChain → BootRecoveryEngine.
        // Schedule a recovery WorkManager job with a 30 s delay so the app finishes
        // startup before attempting to launch MediaProjectionConsentActivity.
        MediaProjectionRecoveryWorker.schedule(
            context,
            reason = "boot_projection_lost",
            attempt = MediaProjectionRecoveryTelemetry.recoveryAttemptCount(context) + 1,
            initialDelaySeconds = 30L,
            policy = ExistingWorkPolicy.REPLACE,
        )
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
