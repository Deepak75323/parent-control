package com.childmonitor.monitoring

import android.content.Context
import kotlin.math.min
import kotlin.random.Random

object MonitoringBackoffPolicy {

    private const val PREFS = "monitoring_backoff"
    private const val KEY_FAILURE_COUNT = "failure_count"
    private const val KEY_NEXT_FGS_AT_MS = "next_fgs_allowed_at_ms"

    /** Base delay after first FGS timeout / start failure. */
    private const val BASE_DELAY_MS = 60_000L

    /** Cap between FGS burst attempts. */
    private const val MAX_DELAY_MS = 3_600_000L

    /** ±10% jitter to avoid synchronized retries across devices. */
    private const val JITTER_RATIO = 0.1

    fun canStartFgs(context: Context, bypassBackoff: Boolean = false): Boolean {
        if (bypassBackoff) return true
        val nextAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_NEXT_FGS_AT_MS, 0L)
        return System.currentTimeMillis() >= nextAt
    }

    fun recordFgsTimeout(context: Context) {
        bumpFailure(context, reason = "timeout")
    }

    fun recordFgsStartFailure(context: Context) {
        bumpFailure(context, reason = "start_failure")
    }

    fun recordFgsSuccess(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FAILURE_COUNT, 0)
            .putLong(KEY_NEXT_FGS_AT_MS, 0L)
            .apply()
    }

    fun nextDelayMs(context: Context): Long {
        val count = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_FAILURE_COUNT, 0)
        return computeDelayMs(count.coerceAtLeast(1))
    }

    fun computeDelayMs(failureCount: Int): Long {
        if (failureCount <= 0) return 0L
        val exponent = (failureCount - 1).coerceAtMost(6)
        val raw = BASE_DELAY_MS shl exponent
        val capped = min(raw, MAX_DELAY_MS)
        val jitter = (capped * JITTER_RATIO * (Random.nextDouble() * 2.0 - 1.0)).toLong()
        return (capped + jitter).coerceAtLeast(BASE_DELAY_MS)
    }

    private fun bumpFailure(context: Context, reason: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_FAILURE_COUNT, 0) + 1
        val delay = computeDelayMs(count)
        val nextAt = System.currentTimeMillis() + delay
        prefs.edit()
            .putInt(KEY_FAILURE_COUNT, count)
            .putLong(KEY_NEXT_FGS_AT_MS, nextAt)
            .apply()
        android.util.Log.w(
            "MonitoringBackoff",
            "FGS backoff reason=$reason count=$count nextDelayMs=$delay nextAt=$nextAt",
        )
    }

    /** @visibleForTesting */
    internal fun resetForTests(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
