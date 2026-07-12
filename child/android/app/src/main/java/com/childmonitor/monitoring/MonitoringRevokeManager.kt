package com.childmonitor.monitoring

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.MonitoringForegroundService
import com.childmonitor.MonitoringPrefs
import com.childmonitor.StreamingHeadlessTaskService
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.capture.StreamCaptureTeardown
import com.childmonitor.deviceowner.DeviceOwnerManager
import com.childmonitor.native.ChildMonitorNativeModule
import com.childmonitor.remote.RemoteControlCommandStore
import com.childmonitor.TamperProtectionManager
import com.childmonitor.sync.NativeStreamingCoordinator
import com.childmonitor.sync.NativeStompOwner

import java.util.concurrent.Executors

/**
 * Single authoritative entry point for parent-initiated monitoring revocation.
 * Idempotent — safe to call from duplicate commands, delayed FCM, or STOMP retries.
 */
object MonitoringRevokeManager {

    private const val TAG = "MonitoringRevoke"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val revokeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "monitoring-revoke").apply { isDaemon = true }
    }
    private val lock = Any()

    @Volatile
    private var revokeInFlight = false

    /**
     * Hard reset of monitoring session state. Android OS permissions are not touched.
     * Work runs off the UI thread; React is notified on the main looper when done.
     */
    @JvmStatic
    fun revokeMonitoringAndStop(context: Context, source: String = "unknown") {
        val app = context.applicationContext
        revokeExecutor.execute {
            synchronized(lock) {
                if (revokeInFlight) {
                    Log.i(TAG, "revoke already in flight — coalescing source=$source")
                }
                revokeInFlight = true
                try {
                    performRevoke(app, source)
                } catch (e: Exception) {
                    Log.e(TAG, "revoke failed source=$source", e)
                } finally {
                    revokeInFlight = false
                }
            }
        }
    }

    private fun performRevoke(context: Context, source: String) {
        Log.i(TAG, "revokeMonitoringAndStop source=$source")

        // Signal JS on the main looper; heavy native work stays on the revoke executor.
        mainHandler.post { ChildMonitorNativeModule.emitStreamControl("STOP") }
        StreamCaptureTeardown.stopAll(context, "revoke_monitoring")
        NativeStreamingCoordinator.stop(context)
        NativeStompOwner.stop()
        StreamingHeadlessTaskService.stop(context)

        DeviceOwnerManager.disableKiosk(context)
        DeviceOwnerManager.clearDeviceOwnerState(context)
        TamperProtectionManager.setHideLauncher(context, false)

        MonitoringPrefs.disarmScreenCaptureAutoApprove(context)
        MediaProjectionTokenStore.clear(context)
        MonitoringPrefs.setPermissionSetupInProgress(context, false)
        MonitoringPrefs.setWizardPersistenceJson(context, null)
        MonitoringPrefs.clear(context)

        RemoteControlCommandStore.clear(context)
        MonitoringWorkScheduler.cancelAll(context)

        context.stopService(Intent(context, MonitoringForegroundService::class.java))

        notifyReact(source)
        Log.i(TAG, "revoke complete source=$source")
    }

    private fun notifyReact(source: String) {
        mainHandler.post {
            ChildMonitorNativeModule.emitMonitoringRevoked(source)
        }
    }
}
