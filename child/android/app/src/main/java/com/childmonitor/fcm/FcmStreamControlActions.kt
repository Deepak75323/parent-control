package com.childmonitor.fcm

/**
 * Parses FCM wake payloads for camera-flip commands.
 * STOMP STREAM_CONTROL can drop under Doze; Flip is also sent as a high-priority FCM data message.
 */
object FcmStreamControlActions {

    /** STOMP and FCM for the same parent flip can arrive several seconds apart. */
    const val DEDUP_WINDOW_MS = 8_000L

    @Volatile
    private var lastAcceptedAtMs = 0L

    fun isSwitchCamera(action: String?, command: String?): Boolean {
        if (action.equals("SWITCH_CAMERA", ignoreCase = true) ||
            action.equals("switch_camera", ignoreCase = true)
        ) {
            return true
        }
        return action.equals("STREAM_CONTROL", ignoreCase = true) &&
            command.equals("SWITCH_CAMERA", ignoreCase = true)
    }

    /**
     * STOMP and FCM can both deliver the same Flip. Accept the first and drop
     * a second toggle inside [DEDUP_WINDOW_MS] so the camera does not flip twice.
     */
    @Synchronized
    fun acceptFlip(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastAcceptedAtMs != 0L && nowMs - lastAcceptedAtMs < DEDUP_WINDOW_MS) {
            return false
        }
        lastAcceptedAtMs = nowMs
        return true
    }

    fun resetForTests() {
        lastAcceptedAtMs = 0L
    }
}

