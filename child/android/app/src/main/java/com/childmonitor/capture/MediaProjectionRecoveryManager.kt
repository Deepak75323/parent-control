package com.childmonitor.capture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.oem.OemVendor
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry

/**
 * Records MediaProjection loss and clears auto-recovery UI launches.
 *
 * Cast / [MediaProjectionConsentActivity] is **never** opened from here.
 * Re-consent happens only via [MediaProjectionConsentHelper.ensureConsent] with
 * [ConsentLaunchSource.SETUP] or [ConsentLaunchSource.PARENT_SCREEN_SHARE].
 */
object MediaProjectionRecoveryManager {

    private const val TAG = "MediaProjectionRecovery"

    /** Retained for tests / telemetry; permission auto-retries no longer consume this budget. */
    const val MAX_RECOVERY_ATTEMPTS = 5

    @Volatile
    private var recoveryInFlight = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Mark projection unavailable and stop automatic Cast launch / retries.
     * Returns false always (no consent UI was launched).
     */
    @Synchronized
    fun attemptRecovery(context: Context, reason: String): Boolean {
        if (ScreenCaptureController.hasLiveProjection()) {
            Log.d(TAG, "attemptRecovery no-op — live projection reason=$reason")
            return false
        }
        if (com.childmonitor.MonitoringPrefs.isPermissionSetupInProgress(context)) {
            Log.d(TAG, "skip mark-lost — permission setup in progress reason=$reason")
            return false
        }

        val app = context.applicationContext
        MediaProjectionRecoveryWorker.cancel(app)
        mainHandler.removeCallbacksAndMessages(null)
        recoveryInFlight = false

        if (MediaProjectionTokenStore.hasValidToken(app)) {
            MediaProjectionTokenStore.invalidateSession(app)
        }

        val state = MediaProjectionTokenStore.getConsentState(app)
        if (state != MediaProjectionConsentState.NEVER_GRANTED &&
            state != MediaProjectionConsentState.REQUIRES_RECONSENT &&
            state != MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH
        ) {
            MediaProjectionTokenStore.markRequiresReconsent(app, reason)
        }

        val oem = OemVendor.detect()
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection marked LOST — no Cast launch (wait for parent Screen or setup)",
            "reason=$reason state=${MediaProjectionTokenStore.getConsentState(app)} oem=${oem.id}",
        )
        Log.w(
            TAG,
            "Projection LOST — Cast NOT launched reason=$reason " +
                "state=${MediaProjectionTokenStore.getConsentState(app)} oem=${oem.id}",
        )
        MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(app, success = false, "no_auto_cast:$reason")
        return false
    }

    /**
     * Parent Screen Share path: warm existing projection or launch Cast when Accessibility is ready.
     * Does **not** use background recovery workers.
     */
    fun attemptRecoveryAsync(context: Context, reason: String, timeoutMs: Long = 45_000L): Boolean {
        if (ScreenCaptureController.hasLiveProjection()) return true
        if (MediaProjectionTokenStore.hasValidToken(context) &&
            MediaProjectionSessionKeeper.ensureReady(context.applicationContext)
        ) {
            return true
        }
        if (MediaProjectionTokenStore.hasValidToken(context)) {
            MediaProjectionTokenStore.invalidateSession(context.applicationContext)
        }
        // Mark lost for telemetry; never auto-launch via attemptRecovery.
        attemptRecovery(context, reason)
        return MediaProjectionConsentHelper.ensureConsent(
            context.applicationContext,
            timeoutMs,
            ConsentLaunchSource.PARENT_SCREEN_SHARE,
        )
    }

    /**
     * Called when setup or parent Start completes consent (success or cancel).
     * Does not schedule automatic permission retries.
     */
    @Synchronized
    fun onRecoveryComplete(context: Context, success: Boolean, reason: String) {
        recoveryInFlight = false
        MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(context, success, reason)
        MediaProjectionRecoveryWorker.cancel(context)
        mainHandler.removeCallbacksAndMessages(null)

        if (success) {
            MediaProjectionLossHandler.onProjectionRestored(context.applicationContext)
            return
        }

        MediaProjectionTokenStore.markRequiresReconsent(context.applicationContext, reason)
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "consent incomplete — waiting for parent Screen or setup (no auto retry)",
            "reason=$reason",
        )
    }

    /** @visibleForTesting */
    internal fun resetForTests() {
        recoveryInFlight = false
        mainHandler.removeCallbacksAndMessages(null)
    }
}
