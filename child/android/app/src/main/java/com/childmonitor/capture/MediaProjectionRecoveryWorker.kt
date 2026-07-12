package com.childmonitor.capture

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.childmonitor.MonitoringPrefs
import com.childmonitor.accessibility.ScreenCaptureAutoApprove
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.oem.OemVendor
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry
import java.util.concurrent.TimeUnit

/**
 * WorkManager-backed recovery for MediaProjection consent lost after process death,
 * low-memory kill, or Android battery-optimization stop.
 *
 * This worker survives process death and Doze mode — neither [android.os.Handler.postDelayed]
 * nor plain [Thread] can guarantee execution after a LMKD kill. WorkManager uses
 * [android.app.job.JobScheduler] (API 23+) which the OS re-queues even after a force-stop.
 *
 * Recovery algorithm:
 * 1. If consent is already valid → success (no-op).
 * 2. If accessibility is ready → delegate to [MediaProjectionRecoveryManager.attemptRecovery].
 * 3. If accessibility is NOT ready → [Result.retry] so WorkManager re-tries with exponential
 *    backoff; the state stays at [MediaProjectionConsentState.RECOVERY_DEFERRED].
 * 4. If max attempts exhausted → [Result.failure]; the parent app must guide the user.
 *
 * OEM notes:
 * - **MIUI/HyperOS**: accessibility-backed BAL is required; without it, `attemptRecovery()`
 *   posts a full-screen-intent notification as a final fallback.
 * - **Samsung One UI**: no special handling needed here; auto-approve covers dialog differences.
 * - **Android 14+ (API 34)**: recovery opens [MediaProjectionConsentActivity] which follows
 *   the correct DATA_SYNC → MEDIA_PROJECTION FGS bootstrap sequence.
 * - **Battery optimization**: [Constraints.requiresBatteryNotLow] is intentionally omitted —
 *   recovery should fire even on low battery since the child cannot capture until consent
 *   is restored.
 *
 * Scheduling:
 * - Initial delay: none (fire as soon as constraints allow).
 * - Backoff: 15 s initial, doubling up to 120 s (matches [MediaProjectionRecoveryManager]).
 * - Unique work name: [WORK_NAME] with [ExistingWorkPolicy.KEEP] — a second enqueue while
 *   a retry is pending is a no-op, preventing thundering-herd after concurrent loss events.
 */
class MediaProjectionRecoveryWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext

        val reason = inputData.getString(KEY_REASON) ?: "work_manager_retry"
        val attempt = inputData.getInt(KEY_ATTEMPT, 1)

        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "RecoveryWorker.doWork",
            "reason=$reason attempt=$attempt runAttemptCount=$runAttemptCount " +
                "api=${Build.VERSION.SDK_INT} oem=${OemVendor.detect().id}",
        )
        Log.i(TAG, "MediaProjectionRecoveryWorker doWork reason=$reason attempt=$attempt runAttemptCount=$runAttemptCount")

        // 1. Already recovered — nothing to do.
        if (MediaProjectionTokenStore.hasValidToken(context)) {
            Log.i(TAG, "doWork — consent already valid, skipping")
            MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(context, success = true, "work_already_valid")
            return Result.success()
        }

        // 2. Not paired — no point recovering.
        if (!MonitoringPrefs.isPaired(context)) {
            Log.d(TAG, "doWork — device not paired, skipping")
            return Result.success()
        }

        // 3. State may not need recovery (e.g. NEVER_GRANTED or setup in progress).
        val state = MediaProjectionTokenStore.getConsentState(context)
        if (!state.blocksScreenShare() || state == MediaProjectionConsentState.NEVER_GRANTED) {
            Log.d(TAG, "doWork — state=$state does not need recovery")
            return Result.success()
        }
        if (MonitoringPrefs.isPermissionSetupInProgress(context)) {
            Log.d(TAG, "doWork — permission setup in progress, skip recovery")
            return Result.retry()
        }

        // 4. Check total attempt budget.
        val totalAttempts = MediaProjectionRecoveryTelemetry.recoveryAttemptCount(context)
        if (totalAttempts >= MediaProjectionRecoveryManager.MAX_RECOVERY_ATTEMPTS) {
            Log.w(TAG, "doWork — max attempts ($totalAttempts) exhausted")
            MediaProjectionTokenStore.markRecoveryFailed(context, "work_max_attempts")
            MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(context, success = false, "work_max_attempts")
            return Result.failure(
                Data.Builder()
                    .putString("failureReason", "max_attempts")
                    .putInt("totalAttempts", totalAttempts)
                    .build(),
            )
        }

        // 5. Accessibility not ready — stay DEFERRED and retry later.
        if (!ScreenCaptureAutoApprove.isReady(context)) {
            Log.w(TAG, "doWork — accessibility not ready, retrying via WorkManager reason=$reason attempt=$attempt")
            MediaProjectionTokenStore.markRecoveryDeferred(context, "work_accessibility_unavailable")
            MediaProjectionRecoveryTelemetry.recordRecoveryAttempt(context, "work_retry_no_accessibility", totalAttempts + 1)
            // Do NOT call Result.retry() more than the worker max — WorkManager enforces its
            // own runAttemptCount limit (default 3 via setMaxAttempts).  We rely on the
            // scheduled-retry mechanism (re-enqueue) instead.
            return if (runAttemptCount < MAX_WORKER_RUN_ATTEMPTS) {
                Result.retry()
            } else {
                // Re-enqueue a fresh job so the full attempt budget is not consumed by accessibility waits.
                scheduleRetry(context, reason, attempt + 1)
                Result.success()
            }
        }

        // 6. Attempt synchronous recovery (accessibility auto-approve path).
        Log.i(TAG, "doWork — triggering MediaProjectionRecoveryManager attempt=$attempt")
        val launched = MediaProjectionRecoveryManager.attemptRecovery(context, "work_manager_attempt_$attempt")

        if (launched) {
            // Recovery was launched — the result will be reported by MediaProjectionConsentActivity
            // via onRecoveryComplete(). Return success so WorkManager does not double-count.
            Log.i(TAG, "doWork — recovery launched via accessibility")
            return Result.success()
        }

        // 7. Recovery not launched for another reason — re-queue.
        Log.w(TAG, "doWork — recovery not launched (state=$state), scheduling retry")
        if (attempt < MediaProjectionRecoveryManager.MAX_RECOVERY_ATTEMPTS) {
            scheduleRetry(context, reason, attempt + 1)
        } else {
            MediaProjectionTokenStore.markRecoveryFailed(context, "work_launch_failed_max")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "MediaProjRecoveryWorker"

        /** Unique work name — prevents duplicate jobs on concurrent loss events. */
        const val WORK_NAME = "mp_recovery"

        private const val KEY_REASON = "reason"
        private const val KEY_ATTEMPT = "attempt"

        /** Max times WorkManager itself re-runs the worker (via Result.retry). */
        private const val MAX_WORKER_RUN_ATTEMPTS = 3

        private const val INITIAL_BACKOFF_SECONDS = 15L
        private const val MAX_BACKOFF_SECONDS = 120L

        /**
         * Schedule a new recovery attempt via WorkManager.
         *
         * Uses [ExistingWorkPolicy.KEEP] so that a concurrent loss event (e.g. process death
         * during a retry window) does not interrupt an already-queued attempt.
         *
         * @param initialDelaySeconds 0 for immediate, otherwise the minimum delay before the
         *   worker can run.
         * @param policy [ExistingWorkPolicy.REPLACE] to force a fresh attempt (e.g. on boot
         *   when conditions have clearly changed).
         */
        @JvmStatic
        @JvmOverloads
        fun schedule(
            context: Context,
            reason: String,
            attempt: Int = 1,
            initialDelaySeconds: Long = 0L,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        ) {
            if (!MonitoringPrefs.isPaired(context)) return

            val data = Data.Builder()
                .putString(KEY_REASON, reason)
                .putInt(KEY_ATTEMPT, attempt)
                .build()

            val request = OneTimeWorkRequestBuilder<MediaProjectionRecoveryWorker>()
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .apply {
                    if (initialDelaySeconds > 0) {
                        setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                    }
                }
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, policy, request)

            PcpFlowLogger.log(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "RecoveryWorker scheduled",
                "reason=$reason attempt=$attempt delayS=$initialDelaySeconds policy=$policy",
            )
            Log.i(TAG, "MediaProjectionRecoveryWorker scheduled reason=$reason attempt=$attempt delayS=$initialDelaySeconds")
        }

        /**
         * Cancel any pending recovery job. Call when consent is restored or explicitly revoked.
         */
        @JvmStatic
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "RecoveryWorker cancelled")
        }

        private fun scheduleRetry(context: Context, reason: String, nextAttempt: Int) {
            val backoffSeconds = (INITIAL_BACKOFF_SECONDS * (1L shl (nextAttempt - 1).coerceAtMost(3)))
                .coerceAtMost(MAX_BACKOFF_SECONDS)
            schedule(context, reason, nextAttempt, backoffSeconds, ExistingWorkPolicy.REPLACE)
        }
    }
}
