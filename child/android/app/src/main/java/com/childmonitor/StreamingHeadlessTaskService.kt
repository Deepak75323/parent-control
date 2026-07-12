package com.childmonitor

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.sync.NativeStreamingCoordinator
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

/**
 * Spins up the RN runtime without UI to handle camera/audio/RN screen streams.
 *
 * P2-4: A [WATCHDOG_INTERVAL_MS]-period watchdog stops the service when no active
 * stream session is detected by the native coordinator.  Without this, a WebRTC
 * PeerConnection that drops silently (e.g., under Doze) could leave the headless
 * task running for the full 1-hour HeadlessJsTaskConfig timeout — draining battery.
 * The watchdog fires every 5 minutes and is a last-resort safety net; the primary
 * teardown path remains [NativeStreamingCoordinator] + [StreamCaptureTeardown].
 */
class StreamingHeadlessTaskService : HeadlessJsTaskService() {

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        scheduleWatchdog()
        Log.d(TAG, "onCreate — watchdog armed interval=${WATCHDOG_INTERVAL_MS}ms")
    }

    override fun onDestroy() {
        cancelWatchdog()
        Log.d(TAG, "onDestroy — watchdog cancelled")
        super.onDestroy()
    }

    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
        if (!MonitoringPrefs.isPaired(this)) return null
        val data = Arguments.createMap()
        data.putString("roomId", intent?.getStringExtra(EXTRA_ROOM_ID))
        data.putString("sessionId", intent?.getStringExtra(EXTRA_SESSION_ID))
        data.putString("streamType", intent?.getStringExtra(EXTRA_STREAM_TYPE))
        data.putString("cameraFacing", intent?.getStringExtra(EXTRA_CAMERA_FACING))
        data.putString("action", intent?.getStringExtra(EXTRA_ACTION) ?: ACTION_START)
        // 1 hour max — stream stop ends the service explicitly; watchdog provides a
        // 5-minute safety net for zombie sessions.
        return HeadlessJsTaskConfig(TASK_KEY, data, 3_600_000L, true)
    }

    private fun scheduleWatchdog() {
        val runnable = object : Runnable {
            override fun run() {
                if (!NativeStreamingCoordinator.hasActiveStreamSession()) {
                    Log.w(
                        TAG,
                        "watchdog: no active stream session detected — " +
                            "stopping zombie headless task (coordinator running=" +
                            "${NativeStreamingCoordinator.isCoordinatorRunning()})",
                    )
                    stop(applicationContext)
                    // Do not reschedule — service is stopping.
                } else {
                    Log.d(TAG, "watchdog: stream active — rescheduling check")
                    watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                }
            }
        }
        watchdogRunnable = runnable
        watchdogHandler.postDelayed(runnable, WATCHDOG_INTERVAL_MS)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { watchdogHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    companion object {
        private const val TAG = "StreamingHeadlessSvc"
        const val TASK_KEY = "ChildMonitoringStream"
        private const val EXTRA_ROOM_ID = "roomId"
        private const val EXTRA_SESSION_ID = "sessionId"
        private const val EXTRA_STREAM_TYPE = "streamType"
        private const val EXTRA_CAMERA_FACING = "cameraFacing"
        private const val EXTRA_ACTION = "action"
        private const val ACTION_START = "start"
        private const val ACTION_SWITCH_CAMERA = "switch_camera"

        /** Interval between watchdog checks — 5 minutes. */
        private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L

        fun start(
            context: Context,
            roomId: String,
            sessionId: String,
            streamType: String,
            cameraFacing: String?,
        ) {
            headlessOwnedJsListener.set(true)
            val intent = Intent(context, StreamingHeadlessTaskService::class.java).apply {
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_STREAM_TYPE, streamType)
                putExtra(EXTRA_CAMERA_FACING, cameraFacing)
                putExtra(EXTRA_ACTION, ACTION_START)
            }
            context.startService(intent)
        }

        fun sendSwitchCamera(context: Context) {
            val intent = Intent(context, StreamingHeadlessTaskService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_SWITCH_CAMERA)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreamingHeadlessTaskService::class.java))
            if (headlessOwnedJsListener.getAndSet(false)) {
                NativeStreamingCoordinator.setJsListenerActive(false)
            }
        }

        private val headlessOwnedJsListener = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}
