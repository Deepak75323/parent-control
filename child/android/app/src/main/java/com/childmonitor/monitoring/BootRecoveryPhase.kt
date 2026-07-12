package com.childmonitor.monitoring

/**
 * Tiered boot recovery — each phase runs heavier work with longer delays.
 *
 * FAST (0s):     heartbeat + commands + policy + STOMP reconnect
 * RECOVERY (1m): full monitoring cycle + FGS burst (WorkManager context, not BOOT_COMPLETED)
 * FALLBACK (5m): full cycle again if prior phases did not restore command delivery
 */
enum class BootRecoveryPhase {
    FAST,
    RECOVERY,
    FALLBACK,
    ;

    companion object {
        fun fromWorkInput(value: String?): BootRecoveryPhase =
            entries.firstOrNull { it.name == value } ?: FAST
    }
}
