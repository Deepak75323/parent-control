package com.childmonitor.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.childmonitor.SilentNotificationHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps camera access valid in background without showing MainActivity.
 * Android 14+ blocks background camera unless a camera-type FGS is already in
 * the foreground state — callers must use [startAndWait] before opening Camera2.
 */
class CameraStreamForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // P2-2: Do NOT call promoteToForeground() here. Android requires startForeground()
        // within ~5 s of startForegroundService() but onStartCommand() is guaranteed to be
        // called immediately after onCreate() on the same thread, so promoting there is
        // sufficient. Calling promoteToForeground() in both lifecycle callbacks caused a
        // redundant startForeground() IPC and a double signalForegroundReady(true) — the
        // second countDown() was harmless but added unnecessary noise.
        Log.i(TAG, "onCreate — camera FGS created, awaiting onStartCommand")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action} startId=$startId")
        return ForegroundServiceCrashGuard.runOnStartCommand(TAG, this) {
            if (!promoteToForeground()) {
                ForegroundServiceCrashGuard.abandonStartup(this, TAG, "camera_promote_failed")
                START_NOT_STICKY
            } else {
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — releasing camera FGS foreground state")
        foregroundActive = false
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        TypedStreamFgsTimeout.handle(this, TAG, StreamKind.CAMERA)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun promoteToForeground(): Boolean {
        val notification = SilentNotificationHelper.buildForegroundNotification(this)
        val cameraGranted = hasCameraPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !cameraGranted) {
            Log.e(
                TAG,
                "Camera FGS blocked — CAMERA not granted (API ${Build.VERSION.SDK_INT}); " +
                    "promoting best-effort then abandoning",
            )
        }
        val promoted = ForegroundServiceCrashGuard.promoteBestEffort(
            service = this,
            notificationId = NOTIFICATION_ID,
            notification = notification,
            preferredTypes = ForegroundServiceCrashGuard.cameraPromotionTypes(cameraGranted),
        )
        if (!promoted) {
            foregroundActive = false
            startRequested = false
            signalForegroundReady(false)
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !cameraGranted) {
            foregroundActive = false
            startRequested = false
            signalForegroundReady(false)
            return false
        }
        foregroundActive = true
        startRequested = false
        Log.i(
            TAG,
            "Camera FGS promoted (API ${Build.VERSION.SDK_INT}) cameraOnly=true " +
                "cameraGranted=$cameraGranted",
        )
        signalForegroundReady(true)
        return true
    }

    companion object {
        private const val TAG = "CameraStreamFgSvc"
        private const val NOTIFICATION_ID = SilentNotificationHelper.NOTIFICATION_ID
        private const val DEFAULT_WAIT_MS = 8000L

        @Volatile
        private var foregroundActive = false

        private val readyLatch = AtomicReference<CountDownLatch?>(null)

        @JvmStatic
        fun isActive(): Boolean = foregroundActive

        private fun hasCameraPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * Starts the camera FGS and blocks until [startForeground] with
         * [FOREGROUND_SERVICE_TYPE_CAMERA] completes (or times out).
         * Must be called on a worker thread — never on the main thread.
         */
        @JvmStatic
        fun startAndWait(context: Context, timeoutMs: Long = DEFAULT_WAIT_MS): Boolean {
            if (foregroundActive) {
                Log.i(TAG, "Camera FGS already active")
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !hasCameraPermission(context)
            ) {
                Log.e(
                    TAG,
                    "startAndWait blocked — CAMERA not granted before CAMERA FGS start",
                )
                return false
            }
            val latch = CountDownLatch(1)
            readyLatch.set(latch)
            try {
                start(context)
                val signaled = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                val ready = signaled && foregroundActive
                if (!ready) {
                    Log.e(TAG, "Camera FGS not ready after ${timeoutMs}ms (signaled=$signaled active=$foregroundActive)")
                }
                return ready
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.e(TAG, "Interrupted waiting for camera FGS", e)
                return false
            } finally {
                readyLatch.compareAndSet(latch, null)
            }
        }

        @Volatile
        private var startRequested = false

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !hasCameraPermission(context)
            ) {
                Log.e(
                    TAG,
                    "start blocked — CAMERA not granted; skipping startForegroundService",
                )
                signalForegroundReady(false)
                return
            }
            startRequested = true
            val intent = Intent(context, CameraStreamForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: SecurityException) {
                startRequested = false
                Log.e(TAG, "SecurityException starting camera foreground service — check CAMERA", e)
                signalForegroundReady(false)
            } catch (e: Exception) {
                startRequested = false
                Log.w(TAG, "Could not start camera foreground service", e)
                signalForegroundReady(false)
            }
        }

        fun stop(context: Context) {
            if (!startRequested && !foregroundActive) {
                return
            }
            readyLatch.get()?.let { latch ->
                try {
                    latch.await(500, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            startRequested = false
            foregroundActive = false
            context.stopService(Intent(context, CameraStreamForegroundService::class.java))
        }

        @JvmStatic
        fun stopSafely(context: Context, reason: String) {
            if (!foregroundActive) return
            Log.i(TAG, "stopSafely reason=$reason")
            stop(context)
        }

        private fun signalForegroundReady(success: Boolean) {
            if (!success) {
                foregroundActive = false
            }
            readyLatch.get()?.countDown()
        }
    }
}
