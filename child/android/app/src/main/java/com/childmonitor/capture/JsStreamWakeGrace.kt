package com.childmonitor.capture

/**
 * Tracks a JS stream wake that was emitted while React is alive but may not process
 * DeviceEventEmitter events immediately (hidden launcher / background on OEM skins).
 *
 * During the grace window native poll must not tear down camera FGS before JS or headless
 * fallback can acquire the session and post an SDP offer.
 */
object JsStreamWakeGrace {

    data class PendingWake(
        val roomId: String,
        val streamType: String,
        val sessionId: String,
        val cameraFacing: String?,
        val sinceMs: Long,
    )

    const val GRACE_MS = 90_000L
    /** Headless capture if JS does not ack wake — keep short so parent session is still alive. */
    const val FALLBACK_MS = 2_500L

    @Volatile
    private var pending: PendingWake? = null

    @JvmStatic
    fun markWakeEmitted(
        roomId: String,
        streamType: String,
        sessionId: String,
        cameraFacing: String?,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        pending = PendingWake(roomId, streamType, sessionId, cameraFacing, nowMs)
    }

    @JvmStatic
    fun clear(sessionId: String? = null) {
        if (sessionId == null || pending?.sessionId == sessionId) {
            pending = null
        }
    }

    @JvmStatic
    fun snapshot(): PendingWake? = pending

    @JvmStatic
    fun isJsCaptureLive(sessionId: String): Boolean =
        StreamSessionRegistry.activeOwner() == StreamSessionRegistry.Owner.JS_HIDDEN &&
            StreamSessionRegistry.activeSessionId() == sessionId

    /**
     * True when an active-poll "empty backend session" miss should be ignored so capture
     * is not torn down mid-start.
     */
    @JvmStatic
    fun shouldSuppressActivePollMiss(
        activeSessionId: String?,
        cameraFgsActive: Boolean,
        audioFgsActive: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val wake = pending
        if (wake != null) {
            if (nowMs - wake.sinceMs > GRACE_MS) {
                pending = null
                return false
            }
            if (isJsCaptureLive(wake.sessionId)) {
                pending = null
                return false
            }
            if (activeSessionId == wake.sessionId) {
                return true
            }
        }
        if (activeSessionId.isNullOrBlank()) return false
        if (!cameraFgsActive && !audioFgsActive) return false
        if (StreamSessionRegistry.activeOwner() != StreamSessionRegistry.Owner.NONE) {
            return false
        }
        // Camera/audio FGS is up for this session but JS has not acquired the registry yet.
        return true
    }
}
