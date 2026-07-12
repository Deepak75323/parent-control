package com.childmonitor.monitoring

/**
 * P1-08 monitoring path under Android 15+ dataSync FGS limits.
 *
 * Platform quota cannot be raised — we degrade gracefully instead.
 */
enum class MonitoringMode {
    /** Pre-Android 15 perpetual FGS, or Android 15+ with healthy burst budget. */
    NORMAL,

    /** Near daily dataSync quota — shorter / fewer bursts; WorkManager preferred. */
    CONSERVATIVE,

    /** dataSync quota exhausted or repeated onTimeout — WorkManager + FCM only. */
    WORKMANAGER_ONLY,

    /** App standby bucket RARE/RESTRICTED — expect sparse JobScheduler; lean on FCM. */
    STANDBY_DEGRADED,
    ;

    fun isDegraded(): Boolean = this != NORMAL
}
