package com.childmonitor.monitoring

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Periodic background monitoring without holding a dataSync foreground service. */
class MonitoringBurstWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!com.childmonitor.MonitoringPrefs.isPaired(applicationContext)) {
                return Result.success()
            }
            Log.i(TAG, "WorkManager monitoring cycle")
            MonitoringDegradationController.evaluate(applicationContext)
            MonitoringBurstEngine.runCycle(applicationContext, startCoordinator = true)
            MonitoringBurstEngine.teardownIfIdle(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "monitoring worker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MonitoringBurstWorker"
    }
}
