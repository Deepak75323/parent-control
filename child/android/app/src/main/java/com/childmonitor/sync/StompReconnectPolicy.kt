package com.childmonitor.sync

import kotlin.math.min
import kotlin.random.Random

/**
 * Backoff for native STOMP WebSocket reconnects.
 *
 * Aggressive linear retries (every few seconds) can exhaust nginx
 * `worker_connections` on a small VM when the proxy is briefly unhealthy —
 * the failure mode that made the parent app show "Could not load device"
 * while the backend container itself was still up.
 */
object StompReconnectPolicy {

    const val MAX_ATTEMPTS = 40
    private const val BASE_DELAY_MS = 2_000L
    private const val MAX_DELAY_MS = 60_000L
    private const val JITTER_RATIO = 0.2

    /**
     * Exponential backoff with jitter for attempt `n` (1-based).
     * attempt 1 → ~2s, 2 → ~4s, 3 → ~8s, … capped at 60s ±20%.
     */
    fun delayMs(attempt: Int, random: Random = Random.Default): Long {
        if (attempt <= 0) return BASE_DELAY_MS
        val exponent = (attempt - 1).coerceAtMost(5)
        val raw = BASE_DELAY_MS shl exponent
        val capped = min(raw, MAX_DELAY_MS)
        val jitter = (capped * JITTER_RATIO * (random.nextDouble() * 2.0 - 1.0)).toLong()
        return (capped + jitter).coerceAtLeast(BASE_DELAY_MS)
    }
}
