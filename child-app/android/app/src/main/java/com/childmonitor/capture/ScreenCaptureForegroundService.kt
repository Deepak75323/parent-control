package com.childmonitor.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.childmonitor.SilentNotificationHelper
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.debug.PermissionDebugLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service for silent screen capture + WebRTC publish (Phase 3.5).
 * Also used to hold mediaProjection FGS for hidden RN screen capture.
 */
class ScreenCaptureForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        promoteToForegroundImmediately()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android kills the process if startForeground() is not called within ~5s of
        // startForegroundService() — promote immediately before any other work.
        when (intent?.action) {
            ACTION_PREPARE, ACTION_START -> promoteToForegroundImmediately()
        }
        when (intent?.action) {
            ACTION_STOP -> {
                StreamLifecycleLogger.log("SERVICE_STOP_REQUEST", "ScreenCaptureForegroundService ACTION_STOP")
                NativeStreamPublisher.stop(applicationContext)
                foregroundActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PREPARE -> {
                val ok = enterForeground(prepareOnly = true)
                if (!ok) {
                    abandonForegroundStartup()
                }
                return if (ok) START_STICKY else START_NOT_STICKY
            }
            ACTION_START -> {
                val roomId = intent.getStringExtra(EXTRA_ROOM_ID)
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.MEDIA_PROJECTION,
                    "ScreenCaptureFgSvc ACTION_START",
                    "room=$roomId session=$sessionId",
                )
                if (!enterForeground()) {
                    Log.e(TAG, "enterForeground failed — aborting screen stream")
                    abandonForegroundStartup()
                    return START_NOT_STICKY
                }
                if (roomId.isNullOrBlank() || sessionId.isNullOrBlank()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                Thread {
                    try {
                        val ok = NativeStreamPublisher.start(applicationContext, roomId, sessionId)
                        if (!ok) {
                            Log.e(TAG, "Native stream start failed")
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Native stream thread exception", e)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }.start()
                // Return START_STICKY so the OS re-delivers the intent if the process is killed
                // mid-stream. Without this, a low-memory kill silently ends the capture.
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun promoteToForegroundImmediately() {
        if (foregroundActive) return
        val notification = SilentNotificationHelper.buildForegroundNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(FOREGROUND_ID, notification)
            }
            foregroundActive = true
        } catch (e: Exception) {
            Log.w(TAG, "typed startForeground failed — trying generic", e)
            try {
                startForeground(FOREGROUND_ID, notification)
                foregroundActive = true
            } catch (e2: Exception) {
                Log.e(TAG, "promoteToForegroundImmediately failed", e2)
            }
        }
    }

    private fun abandonForegroundStartup() {
        // startForegroundService() requires startForeground() within ~5s even on failure paths.
        try {
            val notification = SilentNotificationHelper.buildForegroundNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ScreenCaptureController.activeProjection() != null
            ) {
                startForeground(
                    FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(FOREGROUND_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "abandonForegroundStartup failed", e)
        }
        foregroundActive = false
        signalForegroundReady(false)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun enterForeground(prepareOnly: Boolean = false): Boolean {
        return try {
            // Consent flow: FGS must start before the token is saved — projection comes later.
            if (prepareOnly) {
                promoteToForegroundImmediately()
                val ok = foregroundActive
                if (ok) {
                    Log.i(
                        TAG,
                        "Screen FGS promoted prepareOnly (API ${Build.VERSION.SDK_INT})",
                    )
                } else {
                    Log.e(TAG, "Screen FGS prepareOnly promote failed")
                }
                signalForegroundReady(ok)
                return ok
            }

            if (!ScreenCaptureController.canUseSilentCapture(applicationContext)) {
                Log.e(TAG, "No stored MediaProjection consent")
                signalForegroundReady(false)
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                promoteToForegroundImmediately()
                if (!foregroundActive) {
                    Log.e(TAG, "Screen FGS promote failed before obtainProjection")
                    signalForegroundReady(false)
                    return false
                }
                val projection = ScreenCaptureController.obtainProjection(applicationContext)
                if (projection == null) {
                    Log.w(TAG, "Projection null after FGS on API 34+ — consent may be invalid")
                    signalForegroundReady(false)
                    return false
                }
                Log.i(TAG, "Screen FGS promoted prepareOnly=$prepareOnly (API ${Build.VERSION.SDK_INT})")
                signalForegroundReady(true)
                return true
            }

            val projection = ScreenCaptureController.obtainProjection(applicationContext)
            if (projection == null) {
                Log.w(TAG, "Projection not ready yet — consent kept, FGS not started")
                signalForegroundReady(false)
                return false
            }
            if (!foregroundActive) {
                promoteToForegroundImmediately()
            }
            Log.i(TAG, "Screen FGS promoted prepareOnly=$prepareOnly (API ${Build.VERSION.SDK_INT})")
            signalForegroundReady(true)
            true
        } catch (e: Exception) {
            foregroundActive = false
            Log.e(TAG, "startForeground failed", e)
            signalForegroundReady(false)
            false
        }
    }

    override fun onDestroy() {
        StreamLifecycleLogger.warn("SERVICE_DESTROYED", "ScreenCaptureForegroundService")
        foregroundActive = false
        NativeStreamPublisher.stop(applicationContext)
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        TypedStreamFgsTimeout.handle(this, TAG, StreamKind.SCREEN)
    }

    companion object {
        private const val TAG = "ScreenCaptureFgSvc"
        private const val FOREGROUND_ID = 1004
        private const val DEFAULT_WAIT_MS = 8000L
        private const val ACTION_START = "com.childmonitor.action.START_SCREEN_STREAM"
        private const val ACTION_PREPARE = "com.childmonitor.action.PREPARE_SCREEN_FGS"
        private const val ACTION_STOP = "com.childmonitor.action.STOP_SCREEN_STREAM"
        private const val EXTRA_ROOM_ID = "roomId"
        private const val EXTRA_SESSION_ID = "sessionId"

        @Volatile
        private var foregroundActive = false

        private val readyLatch = AtomicReference<CountDownLatch?>(null)

        @JvmStatic
        fun isForegroundActive(): Boolean = foregroundActive

        @Volatile
        private var startInProgress = false

        /**
         * Start mediaProjection FGS during the one-time consent flow — no stored token required yet.
         */
        @JvmStatic
        fun prepareForegroundForConsent(context: Context, timeoutMs: Long = DEFAULT_WAIT_MS): Boolean {
            PermissionDebugLogger.enter("ScreenCaptureForegroundService.prepareForegroundForConsent", "timeoutMs=$timeoutMs")
            if (Looper.myLooper() == Looper.getMainLooper()) {
                PermissionDebugLogger.error(
                    "ScreenCaptureForegroundService.prepareForegroundForConsent",
                    "refusing sync await on main thread (FGS onStartCommand needs main looper → deadlock)",
                )
                return false
            }
            if (foregroundActive) {
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.MEDIA_PROJECTION,
                    "prepareForegroundForConsent — already active",
                )
                return true
            }
            synchronized(ScreenCaptureForegroundService::class.java) {
                if (startInProgress) {
                    Log.i(TAG, "prepareForegroundForConsent skipped — already in progress")
                    return false
                }
                startInProgress = true
            }
            val latch = CountDownLatch(1)
            readyLatch.set(latch)
            try {
                val intent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
                    action = ACTION_PREPARE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                val signaled = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.MEDIA_PROJECTION,
                    "prepareForegroundForConsent result",
                    "signaled=$signaled foregroundActive=$foregroundActive",
                )
                PermissionDebugLogger.exit(
                    "ScreenCaptureForegroundService.prepareForegroundForConsent",
                    "signaled=$signaled foregroundActive=$foregroundActive",
                )
                return signaled && foregroundActive
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            } catch (e: Exception) {
                Log.e(TAG, "prepareForegroundForConsent failed", e)
                signalForegroundReady(false)
                return false
            } finally {
                readyLatch.compareAndSet(latch, null)
                startInProgress = false
            }
        }

        @JvmStatic
        fun startAndWait(context: Context, timeoutMs: Long = DEFAULT_WAIT_MS): Boolean {
            PermissionDebugLogger.enter("ScreenCaptureForegroundService.startAndWait", "timeoutMs=$timeoutMs")
            if (Looper.myLooper() == Looper.getMainLooper()) {
                PermissionDebugLogger.error(
                    "ScreenCaptureForegroundService.startAndWait",
                    "refusing sync await on main thread (FGS onStartCommand needs main looper → deadlock)",
                )
                return false
            }
            if (!ScreenFgsStartupGate.canStartScreenFgs()) {
                Log.i(TAG, "startAndWait skipped — UI not ready")
                return false
            }
            if (foregroundActive) {
                return ScreenCaptureController.activeProjection() != null ||
                    ScreenCaptureController.obtainProjection(context.applicationContext) != null
            }
            if (!ScreenCaptureController.canUseSilentCapture(context)) {
                return false
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ScreenCaptureController.obtainProjection(context.applicationContext) == null) {
                    Log.w(TAG, "startAndWait skipped — projection not available yet")
                    return false
                }
            }
            synchronized(ScreenCaptureForegroundService::class.java) {
                if (startInProgress) {
                    Log.i(TAG, "startAndWait skipped — already in progress")
                    return false
                }
                startInProgress = true
            }
            val latch = CountDownLatch(1)
            readyLatch.set(latch)
            try {
                val intent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
                    action = ACTION_PREPARE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                val signaled = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                if (!signaled || !foregroundActive) {
                    PermissionDebugLogger.exit("ScreenCaptureForegroundService.startAndWait", "failed signaled=$signaled")
                    return false
                }
                val ok = ScreenCaptureController.activeProjection() != null ||
                    ScreenCaptureController.obtainProjection(context.applicationContext) != null
                PermissionDebugLogger.exit("ScreenCaptureForegroundService.startAndWait", "ok=$ok")
                return ok
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            } catch (e: Exception) {
                Log.e(TAG, "prepare FGS failed", e)
                signalForegroundReady(false)
                return false
            } finally {
                readyLatch.compareAndSet(latch, null)
                startInProgress = false
            }
        }

        fun startStream(context: Context, roomId: String, sessionId: String): Boolean {
            if (roomId.isBlank() || sessionId.isBlank()) return false
            if (!ScreenCaptureController.canUseSilentCapture(context)) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (!startAndWait(context)) {
                    Log.w(TAG, "startStream skipped — could not warm projection session")
                    return false
                }
            } else if (ScreenCaptureController.obtainProjection(context.applicationContext) == null) {
                Log.w(TAG, "startStream skipped — projection not available")
                return false
            }
            val intent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: IllegalStateException) {
                Log.w(TAG, "startForegroundService blocked (app in background): ${e.message}")
                false
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed", e)
                false
            }
        }

        fun stopStream(context: Context) {
            foregroundActive = false
            val intent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        private fun signalForegroundReady(success: Boolean) {
            if (!success) {
                foregroundActive = false
            }
            readyLatch.get()?.countDown()
        }
    }
}
