package com.childmonitor.ring

/**
 * Pure policy for find-my-phone ring duration and restart behaviour.
 * Kept free of Android APIs so unit tests can cover it without Robolectric.
 */
object DeviceRingPolicy {
    /** How long the alarm plays before auto-stop. */
    const val DURATION_MS: Long = 30_000L

    /** Extra wake-lock margin past the ring duration. */
    const val WAKE_LOCK_MARGIN_MS: Long = 5_000L

    fun wakeLockTimeoutMs(durationMs: Long = DURATION_MS): Long =
        durationMs + WAKE_LOCK_MARGIN_MS

    /** A new RING while already playing should restart the alarm from the beginning. */
    fun shouldRestartIfAlreadyPlaying(): Boolean = true

    /** STOP_RING is always safe — no-op if nothing is playing. */
    fun stopIsIdempotent(): Boolean = true
}
