package com.childmonitor.capture

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.platform.AndroidPlatform

/**
 * Blocks [ScreenCaptureForegroundService] until the main UI thread has finished cold start.
 * Starting mediaProjection FGS during React Native init causes
 * ForegroundServiceDidNotStartInTimeException and kills the process.
 */
object ScreenFgsStartupGate {

    private const val TAG = "ScreenFgsStartupGate"
    private const val READY_DELAY_MS = 3000L
    private const val SERVICE_READY_DELAY_MS = 8000L

    @Volatile
    private var screenFgsAllowed = false

    private val handler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun canStartScreenFgs(): Boolean = screenFgsAllowed

    /** Call from [com.childmonitor.MainActivity] when the app becomes visible. */
    @JvmStatic
    fun scheduleMarkReady() {
        handler.removeCallbacks(markReadyRunnable)
        handler.postDelayed(markReadyRunnable, READY_DELAY_MS)
    }

    /**
     * Opens the gate after background boot when MainActivity may never resume
     * (hidden launcher). Delay must exceed React Native cold-start on low-end devices.
     */
    @JvmStatic
    fun scheduleMarkReadyFromService(delayMs: Long = AndroidPlatform.screenFgsColdStartDelayMs()) {
        handler.removeCallbacks(markReadyRunnable)
        handler.postDelayed(markReadyRunnable, delayMs)
    }

    @JvmStatic
    fun markReadyNow() {
        handler.removeCallbacks(markReadyRunnable)
        markReadyRunnable.run()
    }

    private val markReadyRunnable = Runnable {
        if (!screenFgsAllowed) {
            screenFgsAllowed = true
            Log.i(TAG, "screen FGS startup gate open")
        }
    }
}
