package com.childmonitor.fcm

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.monitoring.BootWakeChain
import com.childmonitor.monitoring.MonitoringBurstEngine
import com.childmonitor.monitoring.MonitoringServiceLauncher
import com.childmonitor.sync.DeviceCommandProcessor
import com.childmonitor.sync.NativeStreamingCoordinator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Processes high-priority FCM data wakes (including under Doze).
 *
 * F2 fix: the wake lock is now held until all spawned work completes, not just
 * until the threads are *started*.  Previously, releaseWakeLock() fired in the
 * finally-block immediately after Thread.start() — the spawned threads ran
 * without any wake lock, so the device could sleep mid-command and kill them.
 *
 * Implementation: a CountDownLatch tracks every async branch.  The calling
 * thread blocks for up to WAKE_WORK_TIMEOUT_MS before releasing the lock.
 * The lock itself is still acquired with a hard timeout (WAKE_LOCK_MS) as a
 * safety net against leaked locks.
 */
object FcmWakeHandler {

    private const val TAG = "FcmWakeHandler"
    private const val WAKE_LOCK_TAG = "childmonitor:fcm_wake"
    private const val WAKE_LOCK_MS = 30_000L

    /** Max time to wait for async work to finish before releasing the wake lock. */
    private const val WAKE_WORK_TIMEOUT_MS = 25_000L

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    fun onNewToken(context: Context, token: String) {
        if (token.isBlank()) return
        MonitoringPrefs.setFcmToken(context.applicationContext, token)
        Log.i(TAG, "FCM token persisted")
        if (MonitoringPrefs.isPaired(context)) {
            // F3 fix: schedule a resilient upload via FcmTokenUploadManager instead of a
            // fire-and-forget Thread.  If the upload fails (e.g. no network at install time),
            // it will be retried on the next heartbeat cycle.
            FcmTokenUploadManager.scheduleUpload(context.applicationContext, token)
        }
    }

    fun handleMessage(context: Context, data: Map<String, String>) {
        if (!MonitoringPrefs.isPaired(context)) {
            Log.w(TAG, "Ignoring FCM — device not paired")
            return
        }
        val action = data["action"] ?: run {
            Log.w(TAG, "Ignoring FCM — missing action")
            return
        }
        Log.i(TAG, "FCM wake action=$action")
        acquireWakeLock(context)
        try {
            dispatchWithWakeLock(context, action, data)
        } finally {
            releaseWakeLock()
        }
    }

    /**
     * Runs the FCM action and holds the wake lock until all spawned threads finish
     * (or WAKE_WORK_TIMEOUT_MS elapses, whichever comes first).
     */
    private fun dispatchWithWakeLock(context: Context, action: String, data: Map<String, String>) {
        when (action) {
            "DEVICE_COMMAND" -> {
                val commandId = data["commandId"] ?: return
                val commandType = data["commandType"] ?: return
                // Two independent async branches — track both with a latch.
                val latch = CountDownLatch(2)
                Thread {
                    try {
                        BootWakeChain.runFastRecoveryNow(
                            context.applicationContext,
                            BootWakeChain.Trigger.FCM_ASSIST,
                        )
                    } finally {
                        latch.countDown()
                    }
                }.start()
                Thread {
                    try {
                        DeviceCommandProcessor.handleRealtimeCommand(
                            context.applicationContext,
                            commandId,
                            commandType,
                        )
                    } finally {
                        latch.countDown()
                    }
                }.start()
                // Block the FCM handler thread (which holds the wake lock) until work is done.
                val finished = latch.await(WAKE_WORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (!finished) {
                    Log.w(TAG, "FCM DEVICE_COMMAND work timed out after ${WAKE_WORK_TIMEOUT_MS}ms")
                }
            }
            "STREAM_REQUEST", "STREAM_WAKE" -> {
                val app = context.applicationContext
                val roomId = data["roomId"] ?: data["signalingRoomId"] ?: ""
                val sessionId = data["sessionId"] ?: ""
                val streamType = data["streamType"] ?: "SCREEN"
                val cameraFacing = data["cameraFacing"]
                // Hold the wake lock until the FGS and coordinator are started.
                // Previously this returned immediately and the device could sleep before
                // the stream FGS was promoted — causing the stream to be silently dropped.
                val latch = CountDownLatch(1)
                Thread {
                    try {
                        NativeStreamingCoordinator.start(app)
                        MonitoringBurstEngine.runCycle(app, startCoordinator = true)
                        MonitoringServiceLauncher.requestMonitoring(
                            app,
                            MonitoringServiceLauncher.Reason.FCM,
                            force = true,
                        )
                        if (roomId.isNotBlank() && sessionId.isNotBlank()) {
                            NativeStreamingCoordinator.handleStreamRequestFromWake(
                                app,
                                roomId,
                                streamType,
                                sessionId,
                                cameraFacing,
                            )
                        }
                    } finally {
                        latch.countDown()
                    }
                }.start()
                val finished = latch.await(WAKE_WORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (!finished) {
                    Log.w(TAG, "FCM STREAM_REQUEST work timed out after ${WAKE_WORK_TIMEOUT_MS}ms")
                }
            }
            else -> MonitoringBurstEngine.runCycle(context.applicationContext)
        }
    }

    private fun acquireWakeLock(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_MS)
            wakeLock = lock
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock release failed: ${e.message}")
        } finally {
            wakeLock = null
        }
    }
}
