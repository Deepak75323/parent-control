package com.childmonitor.monitoring

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.NativeSyncUploader
import com.childmonitor.PolicySyncManager
import com.childmonitor.fcm.FcmTokenRegistrar
import com.childmonitor.sync.CommandRecoveryManager
import com.childmonitor.sync.DeviceCommandProcessor
import com.childmonitor.sync.HeartbeatSyncManager
import com.childmonitor.sync.NativeStreamingCoordinator

/**
 * Executes one boot-recovery phase. Safe without a foreground service.
 */
object BootRecoveryEngine {

    private const val TAG = "BootRecoveryEngine"

    data class RecoveryResult(
        val success: Boolean,
        val commandsExecuted: Int,
        val commandLagMs: Long,
        val detail: String,
    )

    fun runPhase(context: Context, phase: BootRecoveryPhase, trigger: String): RecoveryResult {
        if (!MonitoringPrefs.isPaired(context)) {
            return RecoveryResult(false, 0, 0L, "not_paired")
        }
        val app = context.applicationContext
        val bootAt = BootRecoveryTelemetry.bootReceivedAtMs(app)
        val lagMs = if (bootAt > 0L) System.currentTimeMillis() - bootAt else 0L

        BootRecoveryTelemetry.recordPhaseStart(app, phase, trigger)
        Log.i(TAG, "runPhase phase=$phase trigger=$trigger lagMs=$lagMs")

        return try {
            val commandsExecuted = when (phase) {
                BootRecoveryPhase.FAST -> runFastPhase(app)
                BootRecoveryPhase.RECOVERY -> runRecoveryPhase(app)
                BootRecoveryPhase.FALLBACK -> runFallbackPhase(app)
            }
            val result = RecoveryResult(
                success = true,
                commandsExecuted = commandsExecuted,
                commandLagMs = lagMs,
                detail = "ok",
            )
            BootRecoveryTelemetry.recordPhaseComplete(
                app, phase, success = true,
                commandsExecuted = commandsExecuted,
                lagMs = lagMs,
            )
            result
        } catch (e: Exception) {
            Log.e(TAG, "runPhase failed phase=$phase", e)
            val result = RecoveryResult(false, 0, lagMs, e.message ?: "error")
            BootRecoveryTelemetry.recordPhaseComplete(
                app, phase, success = false,
                commandsExecuted = 0,
                lagMs = lagMs,
                detail = e.message,
            )
            result
        }
    }

    /** Lightweight: heartbeat, policy, commands, STOMP — no heavy usage/location sync. */
    private fun runFastPhase(context: Context): Int {
        try {
            HeartbeatSyncManager.syncNow(context)
        } catch (e: Exception) {
            Log.w(TAG, "fast heartbeat failed", e)
        }
        try {
            FcmTokenRegistrar.ensureTokenOnHeartbeat(context)
        } catch (e: Exception) {
            Log.w(TAG, "fast FCM token refresh failed", e)
        }
        try {
            PolicySyncManager.syncPolicies(context)
        } catch (e: Exception) {
            Log.w(TAG, "fast policy sync failed", e)
        }
        NativeStreamingCoordinator.ensureConnected(context)
        CommandRecoveryManager.onProcessStart(context, trigger = "boot_fast")
        return DeviceCommandProcessor.pollAndExecuteWithMetrics(context).executed
    }

    /** Full monitoring cycle + FGS burst when allowed (WorkManager is not BOOT_COMPLETED). */
    private fun runRecoveryPhase(context: Context): Int {
        MonitoringBurstEngine.runCycle(context, startCoordinator = true)
        if (MonitoringPlatform.useBurstFgs()) {
            MonitoringServiceLauncher.requestMonitoring(
                context,
                MonitoringServiceLauncher.Reason.BOOT_RECOVERY,
            )
        }
        return 0
    }

    /** Repeat full cycle; attempt FGS even if backoff was hit earlier. */
    private fun runFallbackPhase(context: Context): Int {
        try {
            NativeSyncUploader.syncAll(context)
        } catch (e: Exception) {
            Log.w(TAG, "fallback syncAll failed", e)
        }
        MonitoringBurstEngine.runCycle(context, startCoordinator = true)
        if (MonitoringPlatform.useBurstFgs()) {
            MonitoringServiceLauncher.requestMonitoring(
                context,
                MonitoringServiceLauncher.Reason.BOOT_FALLBACK,
                force = true,
            )
        }
        return 0
    }
}
