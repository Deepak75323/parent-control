package com.childmonitor.capture

import com.childmonitor.telemetry.StreamLifecycleTelemetry
import java.util.UUID
import kotlin.jvm.JvmOverloads

/**
 * P1-7: Authoritative stream session ownership — JS hidden path vs native publisher.
 * Single lock with explicit lifecycle phases and correlation IDs for telemetry.
 */
object StreamSessionRegistry {

    enum class Owner {
        NONE,
        JS_HIDDEN,
        NATIVE_PUBLISHER,
    }

    enum class LifecyclePhase {
        IDLE,
        ACQUIRING,
        NEGOTIATING,
        ACTIVE,
        RECONNECTING,
        TEARDOWN,
    }

    @Volatile
    private var owner: Owner = Owner.NONE

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var roomId: String? = null

    @Volatile
    private var correlationId: String? = null

    @Volatile
    private var phase: LifecyclePhase = LifecyclePhase.IDLE

    @Volatile
    private var acquiredAtMs: Long = 0L

    @Synchronized
    @JvmOverloads
    fun tryAcquire(requested: Owner, sessionId: String, roomId: String? = null): Boolean {
        if (owner != Owner.NONE && owner != requested && this.sessionId != sessionId) {
            StreamLifecycleLogger.warn(
                "STREAM_SESSION_REPLACED",
                "oldOwner=$owner oldSession=${this.sessionId} newOwner=$requested newSession=$sessionId",
            )
            release(owner)
        }
        if (owner != Owner.NONE && owner != requested) {
            StreamLifecycleLogger.warn(
                "STREAM_SESSION_BLOCKED",
                "owner=$owner requested=$requested session=$sessionId active=${this.sessionId}",
            )
            return false
        }
        owner = requested
        this.sessionId = sessionId
        if (!roomId.isNullOrBlank()) {
            this.roomId = roomId
        }
        if (correlationId == null) {
            correlationId = newCorrelationId()
        }
        phase = LifecyclePhase.ACQUIRING
        acquiredAtMs = System.currentTimeMillis()
        // Clear any stale parent-offline ICE signal from a previous session so we do not
        // tear down the new capturer before the first connected ICE arrives.
        JsViewerPresenceTracker.reset()
        StreamLifecycleTelemetry.record("session_acquired", snapshot())
        return true
    }

    @Synchronized
    fun markPhase(next: LifecyclePhase) {
        phase = next
        StreamLifecycleTelemetry.record("session_phase", snapshot())
    }

    @Synchronized
    fun markActive() {
        phase = LifecyclePhase.ACTIVE
        StreamLifecycleTelemetry.record("session_active", snapshot())
    }

    @Synchronized
    fun markReconnecting() {
        phase = LifecyclePhase.RECONNECTING
        StreamLifecycleTelemetry.record("session_reconnecting", snapshot())
    }

    @Synchronized
    fun release(released: Owner) {
        if (owner == released) {
            phase = LifecyclePhase.TEARDOWN
            StreamLifecycleTelemetry.record("session_released", snapshot())
            owner = Owner.NONE
            sessionId = null
            roomId = null
            correlationId = null
            phase = LifecyclePhase.IDLE
            acquiredAtMs = 0L
        }
    }

    @Synchronized
    fun activeOwner(): Owner = owner

    @Synchronized
    fun activeSessionId(): String? = sessionId

    /** True while WebRTC is still setting up — poll must not tear down the session. */
    @JvmStatic
    @Synchronized
    fun isNegotiatingOrReconnecting(): Boolean =
        phase == LifecyclePhase.ACQUIRING ||
            phase == LifecyclePhase.NEGOTIATING ||
            phase == LifecyclePhase.RECONNECTING

    @Synchronized
    fun activeRoomId(): String? = roomId

    @Synchronized
    fun isBlocked(requested: Owner): Boolean = owner != Owner.NONE && owner != requested

    @Synchronized
    fun snapshot(): Map<String, Any?> = mapOf(
        "owner" to owner.name,
        "sessionId" to sessionId,
        "roomId" to roomId,
        "correlationId" to correlationId,
        "phase" to phase.name,
        "acquiredAtMs" to acquiredAtMs,
        "ageMs" to if (acquiredAtMs > 0) System.currentTimeMillis() - acquiredAtMs else 0L,
    )

    /**
     * Reconcile registry with runtime signals. Returns true when state is consistent.
     */
    @Synchronized
    fun reconcile(nativePublisherRunning: Boolean, jsPeerActive: Boolean): Boolean {
        val orphanNative = owner == Owner.NATIVE_PUBLISHER && !nativePublisherRunning
        val orphanJs = owner == Owner.JS_HIDDEN && !jsPeerActive
        if (orphanNative || orphanJs) {
            StreamLifecycleLogger.warn(
                "SESSION_ORPHAN_DETECTED",
                "owner=$owner nativeRunning=$nativePublisherRunning jsActive=$jsPeerActive session=$sessionId",
            )
            StreamLifecycleTelemetry.record("session_orphan", snapshot())
            release(owner)
            return false
        }
        if (nativePublisherRunning && owner == Owner.NONE && sessionId != null) {
            owner = Owner.NATIVE_PUBLISHER
            phase = LifecyclePhase.ACTIVE
        }
        return true
    }

    private fun newCorrelationId(): String =
        "nat-${System.currentTimeMillis().toString(36)}-${UUID.randomUUID().toString().take(6)}"
}
