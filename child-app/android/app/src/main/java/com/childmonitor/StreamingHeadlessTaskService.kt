package com.childmonitor

import android.content.Context
import android.content.Intent
import com.childmonitor.sync.NativeStreamingCoordinator
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

/**
 * Spins up the RN runtime without UI to handle camera/audio/RN screen streams.
 */
class StreamingHeadlessTaskService : HeadlessJsTaskService() {

    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
        if (!MonitoringPrefs.isPaired(this)) return null
        val data = Arguments.createMap()
        data.putString("roomId", intent?.getStringExtra(EXTRA_ROOM_ID))
        data.putString("sessionId", intent?.getStringExtra(EXTRA_SESSION_ID))
        data.putString("streamType", intent?.getStringExtra(EXTRA_STREAM_TYPE))
        data.putString("cameraFacing", intent?.getStringExtra(EXTRA_CAMERA_FACING))
        data.putString("action", intent?.getStringExtra(EXTRA_ACTION) ?: ACTION_START)
        // 1 hour — stream stop ends the service explicitly.
        return HeadlessJsTaskConfig(TASK_KEY, data, 3_600_000L, true)
    }

    companion object {
        const val TASK_KEY = "ChildMonitoringStream"
        private const val EXTRA_ROOM_ID = "roomId"
        private const val EXTRA_SESSION_ID = "sessionId"
        private const val EXTRA_STREAM_TYPE = "streamType"
        private const val EXTRA_CAMERA_FACING = "cameraFacing"
        private const val EXTRA_ACTION = "action"
        private const val ACTION_START = "start"
        private const val ACTION_SWITCH_CAMERA = "switch_camera"

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
