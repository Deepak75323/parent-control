package com.childmonitor.fcm

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.debug.StreamState
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
 *
 * P2-3 fix: the singleton `@Volatile wakeLock` field is removed. If two FCM
 * messages arrive concurrently, the second acquireWakeLock() would overwrite the
 * field, so the first releaseWakeLock() released the *wrong* lock (the second
 * message's lock), and the first lock leaked until WAKE_LOCK_MS expired. Fix:
 * acquireWakeLock() returns a local reference that is passed directly to
 * releaseWakeLock(), keeping each invocation's lifecycle self-contained.
 */
object FcmWakeHandler {

    private const val TAG = "FcmWakeHandler"
    private const val WAKE_LOCK_TAG = "childmonitor:fcm_wake"
    private const val WAKE_LOCK_MS = 30_000L

    /** Max time to wait for async work to finish before releasing the wake lock. */
    private const val WAKE_WORK_TIMEOUT_MS = 25_000L

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
        PcpFlowLogger.applyTraceFromPayload(data)
        PcpFlowLogger.setState("FCM_RECEIVED")
        PcpFlowLogger.log(PcpFlowLogger.Category.FCM, "handleMessage", "Firebase notification received", data["action"] ?: "[REDACTED]")
        if (!MonitoringPrefs.isPaired(context)) {
            PcpFlowLogger.warn(PcpFlowLogger.Category.FCM, "handleMessage", "Ignoring FCM — device not paired")
            Log.w(TAG, "Ignoring FCM — device not paired")
            return
        }
        val action = data["action"] ?: run {
            PcpFlowLogger.warn(PcpFlowLogger.Category.FCM, "handleMessage", "Ignoring FCM — missing action")
            Log.w(TAG, "Ignoring FCM — missing action")
            return
        }
        PcpFlowLogger.log(PcpFlowLogger.Category.FCM, "handleMessage", "FCM action parsed", "action=$action")
        Log.i(TAG, "FCM wake action=$action traceId=${data["traceId"]}")
        PcpFlowLogger.log(PcpFlowLogger.Category.FCM, "acquireWakeLock", "Acquiring partial wake lock", "timeoutMs=$WAKE_LOCK_MS")
        // P2-3: per-invocation local reference — concurrent FCM messages each hold
        // their own lock and release it independently, with no shared mutable state.
        val lock = acquireWakeLock(context)
        try {
            dispatchWithWakeLock(context, action, data)
        } finally {
            PcpFlowLogger.log(PcpFlowLogger.Category.FCM, "releaseWakeLock", "Releasing wake lock")
            releaseWakeLock(lock)
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
                PcpFlowLogger.setState("FCM_STREAM_REQUEST")
                PcpFlowLogger.transitionState(StreamState.REQUEST_RECEIVED, "dispatchWithWakeLock",
                    "room=$roomId session=$sessionId type=$streamType")
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.FCM,
                    "dispatchWithWakeLock",
                    "STREAM_REQUEST received via FCM",
                    "room=$roomId session=$sessionId type=$streamType facing=$cameraFacing",
                )
                // Hold the wake lock until the FGS and coordinator are started.
                // Previously this returned immediately and the device could sleep before
                // the stream FGS was promoted — causing the stream to be silently dropped.
                val latch = CountDownLatch(1)
                Thread {
                    try {
                        // Open screen-FGS gate on FCM wake — Android 15 burst path does not
                        // open it via legacy monitoring session; without this, native screen
                        // streams die with FGS_GATE_CLOSED while the parent hangs.
                        com.childmonitor.platform.Android16CompatibilityLayer
                            .scheduleScreenFgsGateOpen(app)
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
                        } else {
                            Log.w(
                                TAG,
                                "STREAM_REQUEST missing room/session — cannot start stream " +
                                    "roomBlank=${roomId.isBlank()} sessionBlank=${sessionId.isBlank()}",
                            )
                        }
                    } finally {
                        latch.countDown()
                    }
                }.start()
                val finished = latch.await(WAKE_WORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (!finished) {
                    PcpFlowLogger.failureReport(
                        PcpFlowLogger.Category.FCM,
                        "dispatchWithWakeLock",
                        input = "room=$roomId session=$sessionId",
                        reason = "FCM STREAM_REQUEST work timed out",
                        actual = "timeoutMs=$WAKE_WORK_TIMEOUT_MS",
                        nextCauses = listOf("FGS start blocked", "MediaProjection unavailable", "Device in Doze"),
                    )
                    Log.w(TAG, "FCM STREAM_REQUEST work timed out after ${WAKE_WORK_TIMEOUT_MS}ms")
                } else {
                    PcpFlowLogger.log(PcpFlowLogger.Category.FCM, "dispatchWithWakeLock", "STREAM_REQUEST FCM dispatch complete")
                }
            }
            "HEARTBEAT_NUDGE" -> {
                // Backend proactively wakes devices that have gone quiet longer than its nudge
                // threshold but not yet past the offline cutoff (see HeartbeatNudgeScheduler on
                // the backend). A plain heartbeat POST is enough here — no need to promote a
                // dataSync FGS burst (which would burn Android 15's FGS quota) just to report
                // presence.
                val app = context.applicationContext
                val latch = CountDownLatch(1)
                Thread {
                    try {
                        val ok = com.childmonitor.sync.HeartbeatSyncManager.syncNow(app)
                        Log.i(TAG, "HEARTBEAT_NUDGE handled ok=$ok")
                    } finally {
                        latch.countDown()
                    }
                }.start()
                latch.await(WAKE_WORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            else -> MonitoringBurstEngine.runCycle(context.applicationContext)
        }
    }

    /** Returns a new held wake lock, or null if the system call failed. */
    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_MS)
            Log.d(TAG, "Wake lock acquired held=${lock.isHeld}")
            lock
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock acquire failed: ${e.message}")
            null
        }
    }

    private fun releaseWakeLock(lock: PowerManager.WakeLock?) {
        try {
            if (lock?.isHeld == true) {
                lock.release()
                Log.d(TAG, "Wake lock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock release failed: ${e.message}")
        }
    }
}
