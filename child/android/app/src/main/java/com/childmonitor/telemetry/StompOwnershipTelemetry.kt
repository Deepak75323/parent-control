package com.childmonitor.telemetry

import org.json.JSONObject

/** Telemetry for single STOMP ownership (P1-6). */
object StompOwnershipTelemetry {

    fun recordState(state: String, connected: Boolean) {
        MobileTelemetryReporter.record(
            "stomp_ownership",
            "state_change",
            JSONObject()
                .put("state", state)
                .put("connected", connected)
                .put("owner", "native"),
        )
    }

    fun recordDedup(destination: String) {
        MobileTelemetryReporter.record(
            "stomp_ownership",
            "message_deduplicated",
            JSONObject().put("destination", destination.take(120)),
        )
    }

    fun recordSignalingSubscribe(roomId: String) {
        MobileTelemetryReporter.record(
            "stomp_ownership",
            "signaling_subscribe",
            JSONObject().put("roomId", roomId),
        )
    }

    fun recordSignalingUnsubscribe(roomId: String) {
        MobileTelemetryReporter.record(
            "stomp_ownership",
            "signaling_unsubscribe",
            JSONObject().put("roomId", roomId),
        )
    }

    fun recordJsBridgeRejected(reason: String) {
        MobileTelemetryReporter.record(
            "stomp_ownership",
            "js_stomp_rejected",
            JSONObject().put("reason", reason),
        )
    }
}
