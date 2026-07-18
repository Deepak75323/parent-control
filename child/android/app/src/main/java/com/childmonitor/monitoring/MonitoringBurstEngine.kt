package com.childmonitor.monitoring

import android.content.Context
import android.util.Log
import com.childmonitor.capture.MediaProjectionLossHandler
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.MonitoringPrefs
import com.childmonitor.NativeSyncUploader
import com.childmonitor.PolicySyncManager
import com.childmonitor.sync.DeviceCommandProcessor
import com.childmonitor.sync.NativeStreamingCoordinator

/**
 * Runs one monitoring cycle (sync + command poll). Safe without a foreground service —
 * used from WorkManager, FCM wake locks, and short dataSync FGS bursts.
 */
object MonitoringBurstEngine {

    private const val TAG = "MonitoringBurstEngine"

    fun runCycle(context: Context, startCoordinator: Boolean = true) {
        if (!MonitoringPrefs.isPaired(context)) return
        val app = context.applicationContext
        PresenceHeartbeatScheduler.schedule(app)
        if (MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(app)) {
            MediaProjectionLossHandler.onProjectionUnavailable(app, "burst_cycle")
        }
        try {
            PolicySyncManager.syncPolicies(app)
        } catch (e: Exception) {
            Log.w(TAG, "policy sync failed", e)
        }
        try {
            NativeSyncUploader.syncAll(app)
        } catch (e: Exception) {
            Log.w(TAG, "syncAll failed", e)
        }
        try {
            DeviceCommandProcessor.pollAndExecute(app)
        } catch (e: Exception) {
            Log.w(TAG, "command poll failed", e)
        }
        if (startCoordinator) {
            try {
                NativeStreamingCoordinator.start(app)
            } catch (e: Exception) {
                Log.w(TAG, "coordinator start failed", e)
            }
        }
    }

    fun pollCommandsOnly(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) return
        try {
            DeviceCommandProcessor.pollAndExecute(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "command poll failed", e)
        }
    }

    fun shouldKeepCoordinatorAlive(): Boolean = NativeStreamingCoordinator.hasActiveStreamSession()

    fun teardownIfIdle(context: Context) {
        if (shouldKeepCoordinatorAlive()) return
        NativeStreamingCoordinator.pauseAfterBurst(context.applicationContext)
    }
}
