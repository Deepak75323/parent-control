package com.childmonitor.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
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
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        foregroundActive = false
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        TypedStreamFgsTimeout.handle(this, TAG, StreamKind.CAMERA)
    }

    private fun promoteToForeground() {
        val notification = SilentNotificationHelper.buildForegroundNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundActive = true
            startRequested = false
            Log.i(TAG, "Camera FGS promoted (API ${Build.VERSION.SDK_INT})")
            signalForegroundReady(true)
        } catch (e: Exception) {
            foregroundActive = false
            startRequested = false
            Log.e(TAG, "Camera FGS startForeground failed", e)
            signalForegroundReady(false)
        }
    }

    companion object {
        private const val TAG = "CameraStreamFgSvc"
        private const val NOTIFICATION_ID = 1002
        private const val DEFAULT_WAIT_MS = 8000L

        @Volatile
        private var foregroundActive = false

        private val readyLatch = AtomicReference<CountDownLatch?>(null)

        @JvmStatic
        fun isActive(): Boolean = foregroundActive

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
            startRequested = true
            val intent = Intent(context, CameraStreamForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
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
