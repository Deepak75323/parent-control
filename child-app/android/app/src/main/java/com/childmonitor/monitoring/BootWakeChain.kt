package com.childmonitor.monitoring

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs

/**
 * Schedules tiered post-boot recovery so commands and policy enforcement resume
 * within 1–2 minutes even when FCM is delayed and Android 15 blocks immediate FGS.
 *
 * Chain:
 * 1. FAST (0s)      — heartbeat + commands + policy + STOMP
 * 2. RECOVERY (1m)  — full cycle + FGS burst via WorkManager
 * 3. FALLBACK (5m)  — full cycle + forced FGS attempt
 */
object BootWakeChain {

    private const val TAG = "BootWakeChain"

    enum class Trigger {
        BOOT,
        USER_UNLOCK,
        APP_START,
        FCM_ASSIST,
        NETWORK_AVAILABLE,
    }

    fun schedule(context: Context, trigger: Trigger) {
        if (!MonitoringPrefs.isPaired(context)) return
        val app = context.applicationContext

        if (trigger == Trigger.BOOT) {
            BootRecoveryTelemetry.recordBootReceived(app, trigger.name)
        }

        MonitoringWorkScheduler.ensurePeriodicMonitoring(app)
        MonitoringWorkScheduler.scheduleBootRecoveryChain(app, trigger.name)

        // Pre-API 35 can still start legacy FGS immediately from non-BOOT paths.
        if (!MonitoringPlatform.useBurstFgs() && trigger == Trigger.BOOT) {
            MonitoringServiceLauncher.requestMonitoring(
                app,
                MonitoringServiceLauncher.Reason.BOOT,
            )
        }

        Log.i(TAG, "scheduled boot wake chain trigger=$trigger")
    }

    /** Run fast recovery inline — used from FCM DEVICE_COMMAND for minimal latency. */
    fun runFastRecoveryNow(context: Context, trigger: Trigger) {
        if (!MonitoringPrefs.isPaired(context)) return
        Thread {
            BootRecoveryEngine.runPhase(
                context.applicationContext,
                BootRecoveryPhase.FAST,
                trigger.name,
            )
        }.start()
    }

    /** If app process starts within post-boot window, reinforce the wake chain. */
    fun reinforceIfPostBoot(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) return
        if (!BootRecoveryTelemetry.isWithinPostBootWindow(context)) return
        Log.i(TAG, "reinforcing wake chain — within post-boot window")
        schedule(context, Trigger.APP_START)
    }
}
