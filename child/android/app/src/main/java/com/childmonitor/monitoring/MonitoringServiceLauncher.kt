package com.childmonitor.monitoring

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.childmonitor.MonitoringForegroundService
import com.childmonitor.MonitoringPrefs
import com.childmonitor.SetupAccessNotificationHelper

/**
 * Single entry point for starting monitoring — applies Android 15 backoff and avoids restart loops.
 */
object MonitoringServiceLauncher {

    private const val TAG = "MonitoringLauncher"

    enum class Reason {
        BOOT,
        BOOT_RECOVERY,
        BOOT_FALLBACK,
        APP_START,
        FCM,
        USER,
        USER_UNLOCK,
        TASK_REMOVED,
        TIMEOUT_RECOVERY,
        PERIODIC,
    }

    fun onDevicePaired(context: Context) {
        val app = context.applicationContext
        SetupAccessNotificationHelper.sync(app)
        MonitoringWorkScheduler.ensurePeriodicMonitoring(app)
        requestMonitoring(app, Reason.USER, force = true)
    }

    fun requestMonitoring(context: Context, reason: Reason, force: Boolean = false) {
        if (!MonitoringPrefs.isPaired(context)) return
        val app = context.applicationContext

        MonitoringWorkScheduler.ensurePeriodicMonitoring(app)

        // Android 15: never start FGS directly from BOOT_COMPLETED broadcast path.
        val blockFgsFromBootBroadcast =
            MonitoringPlatform.useBurstFgs() &&
                (reason == Reason.BOOT || reason == Reason.TIMEOUT_RECOVERY)

        if (blockFgsFromBootBroadcast) {
            Log.i(TAG, "FGS blocked from boot broadcast — WorkManager chain handles recovery reason=$reason")
            return
        }

        val workDelay = when (reason) {
            Reason.TIMEOUT_RECOVERY -> MonitoringBackoffPolicy.nextDelayMs(app)
            else -> 0L
        }
        if (reason != Reason.BOOT_RECOVERY && reason != Reason.BOOT_FALLBACK) {
            MonitoringWorkScheduler.scheduleOneShot(app, workDelay, reason.name)
        }

        if (MonitoringPlatform.useBurstFgs()) {
            val forceUrgent = force ||
                reason == Reason.FCM ||
                reason == Reason.USER ||
                reason == Reason.BOOT_RECOVERY ||
                reason == Reason.BOOT_FALLBACK ||
                reason == Reason.USER_UNLOCK
            val bypassBackoff = forceUrgent
            if (!MonitoringBackoffPolicy.canStartFgs(app, bypassBackoff)) {
                Log.i(TAG, "FGS burst deferred (backoff) reason=$reason")
                MonitoringBurstEngine.runCycle(app, startCoordinator = true)
                MonitoringBurstEngine.teardownIfIdle(app)
                return
            }
            // P1-08: skip dataSync FGS when quota/standby forces WorkManager-only path.
            if (MonitoringDegradationController.shouldSkipBurstFgs(app, forceUrgent)) {
                Log.i(
                    TAG,
                    "FGS burst skipped (P1-08 degrade) reason=$reason " +
                        "mode=${MonitoringDegradationController.currentMode(app)}",
                )
                MonitoringBurstEngine.runCycle(app, startCoordinator = true)
                MonitoringBurstEngine.teardownIfIdle(app)
                MonitoringWorkScheduler.scheduleOneShot(app, 0L, "p1_08_degraded_${reason.name}")
                return
            }
            startBurstFgs(app, reason)
        } else {
            startLegacyFgs(app)
        }
    }

    private fun startBurstFgs(context: Context, reason: Reason) {
        val intent = Intent(context, MonitoringForegroundService::class.java).apply {
            action = MonitoringForegroundService.ACTION_BURST
            putExtra(MonitoringForegroundService.EXTRA_REASON, reason.name)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "started burst FGS reason=$reason")
        } catch (e: Exception) {
            Log.e(TAG, "burst FGS start failed reason=$reason", e)
            MonitoringBackoffPolicy.recordFgsStartFailure(context.applicationContext)
            MonitoringDegradationController.onFgsStartFailure(context.applicationContext)
        }
    }

    private fun startLegacyFgs(context: Context) {
        val intent = Intent(context, MonitoringForegroundService::class.java).apply {
            action = MonitoringForegroundService.ACTION_LEGACY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "legacy FGS start failed", e)
        }
    }
}
