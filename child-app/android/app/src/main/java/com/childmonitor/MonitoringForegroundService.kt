package com.childmonitor

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.childmonitor.capture.MediaProjectionConsentState
import com.childmonitor.capture.MediaProjectionLossHandler
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.capture.ScreenFgsStartupGate
import com.childmonitor.capture.StreamLifecycleLogger
import com.childmonitor.monitoring.BootRecoveryTelemetry
import com.childmonitor.monitoring.MonitoringBackoffPolicy
import com.childmonitor.monitoring.MonitoringBurstEngine
import com.childmonitor.monitoring.MonitoringPlatform
import com.childmonitor.monitoring.MonitoringServiceLauncher
import com.childmonitor.monitoring.MonitoringWorkScheduler
import com.childmonitor.sync.NativeStreamingCoordinator

class MonitoringForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var burstMode = false
    private var finishingBurst = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val legacySyncRunnable = object : Runnable {
        override fun run() {
            Thread { MonitoringBurstEngine.runCycle(applicationContext) }.start()
            handler.postDelayed(this, LEGACY_SYNC_INTERVAL_MS)
        }
    }

    private val legacyCommandPollRunnable = object : Runnable {
        override fun run() {
            if (MonitoringPrefs.isPaired(this@MonitoringForegroundService)) {
                Thread { MonitoringBurstEngine.pollCommandsOnly(applicationContext) }.start()
            }
            handler.postDelayed(this, LEGACY_COMMAND_POLL_MS)
        }
    }

    private val burstCommandPollRunnable = object : Runnable {
        override fun run() {
            if (!MonitoringPrefs.isPaired(this@MonitoringForegroundService)) return
            Thread { MonitoringBurstEngine.pollCommandsOnly(applicationContext) }.start()
            handler.postDelayed(this, BURST_COMMAND_POLL_MS)
        }
    }

    private val burstFinishRunnable = Runnable {
        finishBurstSafely("burst_duration_elapsed")
    }

    override fun onCreate() {
        super.onCreate()
        burstMode = MonitoringPlatform.useBurstFgs()
        registerNetworkCallback()
        if (!burstMode) {
            startLegacySession()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!MonitoringPrefs.isPaired(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (MonitoringPlatform.useBurstFgs()) {
            burstMode = true
            if (intent?.action == ACTION_LEGACY) {
                startBurstSession("legacy_action_on_api35")
            } else {
                startBurstSession(intent?.getStringExtra(EXTRA_REASON) ?: "onStartCommand")
            }
            return START_NOT_STICKY
        }

        Thread { MonitoringBurstEngine.runCycle(applicationContext) }.start()
        NativeStreamingCoordinator.start(this)
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            super.onTimeout(startId, fgsType)
            return
        }
        Log.w(TAG, "dataSync FGS timeout — stopping gracefully fgsType=$fgsType")
        burstActive = false
        MonitoringBackoffPolicy.recordFgsTimeout(applicationContext)
        handler.removeCallbacksAndMessages(null)
        MonitoringBurstEngine.teardownIfIdle(applicationContext)
        MonitoringWorkScheduler.scheduleOneShot(
            applicationContext,
            MonitoringBackoffPolicy.nextDelayMs(applicationContext),
            "fgs_timeout",
        )
        if (BootRecoveryTelemetry.isWithinPostBootWindow(applicationContext)) {
            MonitoringWorkScheduler.scheduleBootRecoveryChain(applicationContext, "fgs_timeout")
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!MonitoringPrefs.isPaired(this)) return
        Log.i(TAG, "Task removed — scheduling WorkManager (no FGS restart loop)")
        MonitoringWorkScheduler.scheduleOneShot(applicationContext, 0L, "task_removed")
        if (!MonitoringPlatform.useBurstFgs()) {
            MonitoringServiceLauncher.requestMonitoring(
                applicationContext,
                MonitoringServiceLauncher.Reason.TASK_REMOVED,
            )
        }
    }

    override fun onDestroy() {
        StreamLifecycleLogger.warn("SERVICE_DESTROYED", "MonitoringForegroundService burst=$burstMode")
        handler.removeCallbacksAndMessages(null)
        unregisterNetworkCallback()
        if (!MonitoringBurstEngine.shouldKeepCoordinatorAlive()) {
            NativeStreamingCoordinator.stop(this)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Network available — triggering STOMP reconnect + burst")
                    // Reconnect STOMP and run a sync burst so commands are not missed
                    // after switching from Wi-Fi to mobile data or after airplane mode.
                    Thread {
                        com.childmonitor.sync.NativeStompOwner.ensureConnected(applicationContext)
                        MonitoringBurstEngine.runCycle(applicationContext)
                    }.start()
                }
                override fun onLost(network: Network) {
                    Log.i(TAG, "Network lost — coordinator will retry on reconnect")
                }
            }
            cm.registerNetworkCallback(request, cb)
            networkCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback registration failed: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            val cb = networkCallback ?: return
            val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        } finally {
            networkCallback = null
        }
    }

    private fun startLegacySession() {
        promoteToForeground()
        handler.post(legacySyncRunnable)
        handler.post(legacyCommandPollRunnable)
        if (MonitoringPrefs.isPaired(this)) {
            when (MediaProjectionTokenStore.getConsentState(this)) {
                MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
                MediaProjectionConsentState.REQUIRES_RECONSENT,
                MediaProjectionConsentState.RECOVERY_FAILED,
                -> MediaProjectionLossHandler.onProjectionUnavailable(applicationContext, "process_restart")
                else -> { }
            }
            ScreenFgsStartupGate.scheduleMarkReadyFromService()
            NativeStreamingCoordinator.start(this)
        }
    }

    private fun startBurstSession(reason: String) {
        if (finishingBurst) return
        burstActive = true
        promoteToForeground()
        handler.removeCallbacks(burstFinishRunnable)
        handler.removeCallbacks(burstCommandPollRunnable)
        Log.i(TAG, "Starting FGS burst reason=$reason durationMs=$BURST_DURATION_MS")
        Thread { MonitoringBurstEngine.runCycle(applicationContext) }.start()
        handler.post(burstCommandPollRunnable)
        handler.postDelayed(burstFinishRunnable, BURST_DURATION_MS)
    }

    private fun finishBurstSafely(reason: String) {
        if (finishingBurst) return
        if (MonitoringBurstEngine.shouldKeepCoordinatorAlive()) {
            Log.i(TAG, "Extending burst — active stream session reason=$reason")
            finishingBurst = false
            handler.removeCallbacks(burstFinishRunnable)
            handler.postDelayed(burstFinishRunnable, BURST_DURATION_MS)
            return
        }
        finishingBurst = true
        burstActive = false
        handler.removeCallbacks(burstCommandPollRunnable)
        handler.removeCallbacks(burstFinishRunnable)
        Log.i(TAG, "Finishing FGS burst reason=$reason activeStream=${MonitoringBurstEngine.shouldKeepCoordinatorAlive()}")
        MonitoringBackoffPolicy.recordFgsSuccess(applicationContext)
        MonitoringBurstEngine.teardownIfIdle(applicationContext)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun promoteToForeground() {
        val notification = SilentNotificationHelper.buildForegroundNotification(this)
        val id = SilentNotificationHelper.notificationId()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(id, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "startForeground failed", e)
            MonitoringBackoffPolicy.recordFgsStartFailure(applicationContext)
        }
    }

    companion object {
        const val ACTION_BURST = "com.childmonitor.action.MONITORING_BURST"
        const val ACTION_LEGACY = "com.childmonitor.action.MONITORING_LEGACY"
        const val EXTRA_REASON = "reason"

        private const val TAG = "MonitoringFgSvc"

        @Volatile
        private var burstActive = false

        /** True while a dataSync FGS burst is running (Android 15+). */
        @JvmStatic
        fun isBurstActive(): Boolean = burstActive

        /** Short dataSync window — stays well under Android 15's 6h/24h quota. */
        private const val BURST_DURATION_MS = 90_000L
        private const val BURST_COMMAND_POLL_MS = 15_000L

        private const val LEGACY_SYNC_INTERVAL_MS = 5 * 60 * 1000L
        private const val LEGACY_COMMAND_POLL_MS = 15 * 1000L
    }
}
