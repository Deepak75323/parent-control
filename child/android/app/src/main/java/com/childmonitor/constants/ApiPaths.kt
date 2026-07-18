package com.childmonitor.constants

/**
 * Central API path constants for native child HTTP clients.
 * Keep values identical to backend routes / child RN ApiPaths.
 */
object ApiPaths {
    const val V1 = "/api/v1"
    const val V1_PREFIX = "$V1/"

    const val DEVICE = "$V1/device"
    const val DEVICE_PAIR = "$DEVICE/pair"
    const val DEVICE_HEARTBEAT = "$DEVICE/heartbeat"
    const val DEVICE_NOTIFICATIONS = "$DEVICE/notifications"
    const val DEVICE_LOCATION = "$DEVICE/location"
    const val DEVICE_USAGE_STATS = "$DEVICE/usage-stats"
    const val DEVICE_INSTALLED_APPS = "$DEVICE/installed-apps"
    const val DEVICE_COMMANDS_PENDING = "$DEVICE/commands/pending"
    const val DEVICE_COMMANDS_IN_FLIGHT = "$DEVICE/commands/in-flight"
    const val DEVICE_TAMPER_ATTEMPT = "$DEVICE/tamper/attempt"
    const val DEVICE_TAMPER_VERIFY = "$DEVICE/tamper/verify"
    const val DEVICE_TAMPER_OFFLINE_PIN = "$DEVICE/tamper/offline-pin"
    const val DEVICE_TAMPER_ADMIN_DISABLED = "$DEVICE/tamper/admin-disabled"
    const val DEVICE_TELEMETRY_BATCH = "$DEVICE/telemetry/batch"

    const val CHILD_POLICIES = "$V1/child/policies"
    const val CHILD_POLICIES_ACK = "$CHILD_POLICIES/ack"
    const val CHILD_STREAMING_PENDING = "$V1/child/streaming/pending"
    const val CHILD_STREAMING_ACTIVE = "$V1/child/streaming/active"

    const val EVENTS_POLICY_VIOLATIONS = "$V1/events/policy-violations"

    const val MONITORING_SIGNALING = "$V1/monitoring/signaling"
    const val MONITORING_WEBRTC_CONFIG = "$V1/monitoring/webrtc-config"

    fun signaling(roomId: String): String = "$MONITORING_SIGNALING/$roomId"

    fun commandClaim(commandId: String): String = "$DEVICE/commands/$commandId/claim"

    fun commandAck(commandId: String): String = "$DEVICE/commands/$commandId/ack"

    fun commandExecuted(commandId: String): String = "$DEVICE/commands/$commandId/executed"

    fun streamSessionFailure(sessionId: String): String =
        "$DEVICE/stream-sessions/$sessionId/failure"

    fun webrtcConfig(sessionId: String?): String =
        if (sessionId != null) {
            "$MONITORING_WEBRTC_CONFIG?sessionId=$sessionId"
        } else {
            MONITORING_WEBRTC_CONFIG
        }
}
