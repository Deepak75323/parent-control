package com.childmonitor.sync

/**
 * Pure handoff policy for native/JS stream routing — testable without Android coordinator init.
 */
object NativeStreamingHandoffPolicy {

    fun isSameSessionAlreadyLive(
        activeSessionId: String?,
        activeRoomId: String?,
        requestedSessionId: String,
        requestedRoomId: String,
        nativePublisherRunning: Boolean,
        registrySessionId: String?,
        screenFgsActive: Boolean,
        cameraActive: Boolean,
        audioActive: Boolean,
        startInProgress: Boolean,
    ): Boolean {
        if (activeSessionId != requestedSessionId || activeRoomId != requestedRoomId) {
            return false
        }
        return nativePublisherRunning ||
            registrySessionId == requestedSessionId ||
            screenFgsActive ||
            cameraActive ||
            audioActive ||
            startInProgress
    }
}
