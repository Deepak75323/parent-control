package com.childmonitor.monitoring

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Executes one tier of the boot recovery chain (FAST / RECOVERY / FALLBACK).
 */
class BootRecoveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!com.childmonitor.MonitoringPrefs.isPaired(applicationContext)) {
            return Result.success()
        }
        val phase = BootRecoveryPhase.fromWorkInput(inputData.getString(KEY_PHASE))
        val trigger = inputData.getString(KEY_TRIGGER) ?: "workmanager"
        Log.i(TAG, "BootRecoveryWorker phase=$phase trigger=$trigger")
        val result = BootRecoveryEngine.runPhase(applicationContext, phase, trigger)
        MonitoringBurstEngine.teardownIfIdle(applicationContext)
        return if (result.success) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "BootRecoveryWorker"
        const val KEY_PHASE = "phase"
        const val KEY_TRIGGER = "trigger"
    }
}
