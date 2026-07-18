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
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.oem.OemVendor
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry
import java.util.concurrent.TimeUnit

/**
 * Legacy WorkManager job for projection-loss bookkeeping only.
 *
 * Does **not** open the MediaProjection / Cast dialog. Re-consent is only via
 * setup ([ConsentLaunchSource.SETUP]) or parent Start Screen
 * ([ConsentLaunchSource.PARENT_SCREEN_SHARE]).
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
            "RecoveryWorker.doWork — mark LOST only (no Cast)",
            "reason=$reason attempt=$attempt api=${Build.VERSION.SDK_INT} oem=${OemVendor.detect().id}",
        )
        Log.i(TAG, "MediaProjectionRecoveryWorker mark-lost-only reason=$reason (Cast NOT launched)")

        if (MediaProjectionTokenStore.hasValidToken(context)) {
            MediaProjectionRecoveryTelemetry.recordRecoveryOutcome(context, success = true, "work_already_valid")
            return Result.success()
        }
        if (!MonitoringPrefs.isPaired(context)) {
            return Result.success()
        }

        MediaProjectionRecoveryManager.attemptRecovery(context, "work_manager:$reason")
        return Result.success()
    }

    companion object {
        private const val TAG = "MediaProjRecoveryWorker"
        const val WORK_NAME = "media_projection_recovery"
        private const val KEY_REASON = "reason"
        private const val KEY_ATTEMPT = "attempt"

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            } catch (e: Exception) {
                Log.w(TAG, "cancel failed: ${e.message}")
            }
        }

        fun schedule(
            context: Context,
            reason: String,
            attempt: Int = 1,
            initialDelaySeconds: Long = 0L,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        ) {
            // Still allow enqueue so boot/loss bookkeeping runs once — but doWork never opens Cast.
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val data = Data.Builder()
                .putString(KEY_REASON, reason)
                .putInt(KEY_ATTEMPT, attempt)
                .build()
            val request = OneTimeWorkRequestBuilder<MediaProjectionRecoveryWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setInitialDelay(initialDelaySeconds.coerceAtLeast(0L), TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            try {
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(WORK_NAME, policy, request)
                Log.i(TAG, "scheduled mark-lost work reason=$reason delayS=$initialDelaySeconds (no Cast)")
            } catch (e: Exception) {
                Log.w(TAG, "schedule failed: ${e.message}")
            }
        }
    }
}
