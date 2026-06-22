package com.childmonitor.monitoring

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MonitoringWorkScheduler {

    private const val TAG = "MonitoringWorkScheduler"
    private const val PERIODIC_NAME = "monitoring_periodic_sync"
    private const val ONESHOT_NAME = "monitoring_oneshot_sync"
    private const val BOOT_FAST_NAME = "boot_recovery_fast"
    private const val BOOT_RECOVERY_NAME = "boot_recovery_1m"
    private const val BOOT_FALLBACK_NAME = "boot_recovery_5m"

    /** Minimum interval WorkManager allows for periodic work. */
    private const val PERIODIC_INTERVAL_MIN = 15L

    private const val RECOVERY_DELAY_MS = 60_000L
    private const val FALLBACK_DELAY_MS = 5 * 60_000L

    fun ensurePeriodicMonitoring(context: Context) {
        if (!com.childmonitor.MonitoringPrefs.isPaired(context)) return
        val request = PeriodicWorkRequestBuilder<MonitoringBurstWorker>(
            PERIODIC_INTERVAL_MIN,
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Log.i(TAG, "scheduled periodic monitoring every ${PERIODIC_INTERVAL_MIN}m")
    }

    /**
     * Tiered boot wake chain — separate unique work names so phases are not cancelled
     * by unrelated one-shot monitoring work.
     */
    fun scheduleBootRecoveryChain(context: Context, trigger: String) {
        if (!com.childmonitor.MonitoringPrefs.isPaired(context)) return
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)

        val fastBuilder = OneTimeWorkRequestBuilder<BootRecoveryWorker>()
            .setInputData(
                workData(BootRecoveryPhase.FAST, trigger),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            fastBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        wm.enqueueUniqueWork(
            BOOT_FAST_NAME,
            ExistingWorkPolicy.REPLACE,
            fastBuilder.build(),
        )

        val networkConnected = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        wm.enqueueUniqueWork(
            BOOT_RECOVERY_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BootRecoveryWorker>()
                .setInitialDelay(RECOVERY_DELAY_MS, TimeUnit.MILLISECONDS)
                .setConstraints(networkConnected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workData(BootRecoveryPhase.RECOVERY, trigger))
                .build(),
        )

        wm.enqueueUniqueWork(
            BOOT_FALLBACK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BootRecoveryWorker>()
                .setInitialDelay(FALLBACK_DELAY_MS, TimeUnit.MILLISECONDS)
                .setConstraints(networkConnected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workData(BootRecoveryPhase.FALLBACK, trigger))
                .build(),
        )

        Log.i(
            TAG,
            "scheduled boot recovery chain trigger=$trigger " +
                "fast=0ms recovery=${RECOVERY_DELAY_MS}ms fallback=${FALLBACK_DELAY_MS}ms",
        )
    }

    fun scheduleOneShot(context: Context, delayMs: Long = 0L, reason: String = "unspecified") {
        if (!com.childmonitor.MonitoringPrefs.isPaired(context)) return
        val builder = OneTimeWorkRequestBuilder<MonitoringBurstWorker>()
        if (delayMs > 0L) {
            builder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        }
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONESHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            builder.build(),
        )
        Log.i(TAG, "scheduled one-shot monitoring reason=$reason delayMs=$delayMs")
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(PERIODIC_NAME)
        wm.cancelUniqueWork(ONESHOT_NAME)
        wm.cancelUniqueWork(BOOT_FAST_NAME)
        wm.cancelUniqueWork(BOOT_RECOVERY_NAME)
        wm.cancelUniqueWork(BOOT_FALLBACK_NAME)
    }

    fun isScheduled(context: Context): Boolean {
        return try {
            val wm = WorkManager.getInstance(context.applicationContext)
            val periodic = wm.getWorkInfosForUniqueWork(PERIODIC_NAME).get()
            periodic.any {
                it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.RUNNING
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun workData(phase: BootRecoveryPhase, trigger: String): Data =
        Data.Builder()
            .putString(BootRecoveryWorker.KEY_PHASE, phase.name)
            .putString(BootRecoveryWorker.KEY_TRIGGER, trigger)
            .build()
}
