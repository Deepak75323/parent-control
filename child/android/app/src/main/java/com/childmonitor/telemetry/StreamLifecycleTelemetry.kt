package com.childmonitor.telemetry

import android.util.Log
import com.childmonitor.capture.StreamLifecycleLogger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * P1-7: In-process stream lifecycle / ICE recovery telemetry for heartbeat export.
 */
object StreamLifecycleTelemetry {

    private const val TAG = "PCP_STREAM_TELEMETRY"
    private const val MAX_EVENTS = 40

    private val iceRestartAttempts = AtomicInteger(0)
    private val iceRecoverySuccess = AtomicInteger(0)
    private val iceRecoveryFailed = AtomicInteger(0)
    private val orphanCleanups = AtomicInteger(0)
    private val recentEvents = ConcurrentLinkedQueue<String>()

    fun record(event: String, attrs: Map<String, Any?> = emptyMap()) {
        val detail = attrs.entries.joinToString(",") { "${it.key}=${it.value}" }
        val line = if (detail.isBlank()) event else "$event|$detail"
        recentEvents.add(line)
        while (recentEvents.size > MAX_EVENTS) {
            recentEvents.poll()
        }
        Log.i(TAG, line)
        when (event) {
            "ice_restart_attempt" -> iceRestartAttempts.incrementAndGet()
            "ice_recovered" -> iceRecoverySuccess.incrementAndGet()
            "ice_recovery_failed" -> iceRecoveryFailed.incrementAndGet()
            "session_orphan" -> orphanCleanups.incrementAndGet()
        }
        StreamLifecycleLogger.log("TELEMETRY_$event", detail.ifBlank { null })
    }

    fun metricsSnapshot(): Map<String, Any> = mapOf(
        "iceRestartAttempts" to iceRestartAttempts.get(),
        "iceRecoverySuccess" to iceRecoverySuccess.get(),
        "iceRecoveryFailed" to iceRecoveryFailed.get(),
        "orphanCleanups" to orphanCleanups.get(),
        "recentEvents" to recentEvents.toList(),
    )
}
