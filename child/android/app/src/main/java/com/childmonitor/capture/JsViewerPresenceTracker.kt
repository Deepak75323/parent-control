package com.childmonitor.capture

/**
 * Tracks JS WebRTC ICE connection state so native poll/watchdog can release camera/mic when the
 * parent viewer disappears — even if the backend session is still ACTIVE (parent killed
 * the app or lost network without tapping Stop).
 *
 * Only accepts real ICE states. PeerConnection [connectionState] values like "connecting"
 * must never be fed here — they would falsely expire capture mid-negotiation.
 */
object JsViewerPresenceTracker {

    private const val DISCONNECTED_GRACE_MS = 20_000L
    private const val TERMINAL_GRACE_MS = 8_000L

    private val ICE_STATES = setOf(
        "new",
        "checking",
        "connected",
        "completed",
        "disconnected",
        "failed",
        "closed",
    )

    @Volatile
    private var lastIceState: String? = null

    @Volatile
    private var lastIceChangeMs: Long = 0L

    /** Test seam — wall clock by default. */
    @Volatile
    var nowMs: () -> Long = { System.currentTimeMillis() }

    @Synchronized
    fun onIceState(state: String?) {
        if (state.isNullOrBlank()) return
        val normalized = state.lowercase().trim()
        if (normalized !in ICE_STATES) return
        // Keep the grace clock running for sticky bad states; do not reset on identical reports.
        if (normalized == lastIceState) return
        lastIceState = normalized
        lastIceChangeMs = nowMs()
    }

    @Synchronized
    fun reset() {
        lastIceState = null
        lastIceChangeMs = 0L
    }

    /** True when local capture should stop because the parent viewer ICE path is dead. */
    @Synchronized
    fun shouldReleaseCapture(): Boolean {
        val state = lastIceState ?: return false
        when (state) {
            "connected", "completed", "new", "checking" -> return false
            "disconnected" -> {
                return nowMs() - lastIceChangeMs >= DISCONNECTED_GRACE_MS
            }
            "failed", "closed" -> {
                return nowMs() - lastIceChangeMs >= TERMINAL_GRACE_MS
            }
            else -> return false
        }
    }

    @Synchronized
    fun snapshot(): Map<String, Any?> = mapOf(
        "lastIceState" to lastIceState,
        "lastIceChangeMs" to lastIceChangeMs,
        "ageMs" to if (lastIceChangeMs > 0) nowMs() - lastIceChangeMs else 0L,
    )
}
