package com.childmonitor.remote

/**
 * Lifecycle state for locally persisted remote-control commands.
 */
enum class RemoteControlCommandDurabilityState {
    /** Waiting for AccessibilityService reconnect. */
    QUEUED,
    /** Expired or overflow — failure ACK pending or sent. */
    DEAD_LETTER,
    ;

    companion object {
        fun fromPersisted(name: String?): RemoteControlCommandDurabilityState =
            entries.firstOrNull { it.name == name } ?: QUEUED
    }
}
