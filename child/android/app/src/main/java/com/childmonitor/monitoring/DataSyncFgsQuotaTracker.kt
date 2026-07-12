package com.childmonitor.monitoring

import android.content.Context
import android.util.Log
import com.childmonitor.platform.AndroidPlatform
import java.util.concurrent.TimeUnit

/**
 * P1-08 — local estimate of Android 15+ dataSync FGS daily usage.
 *
 * The OS does not expose remaining quota. We track burst wall-time and timeouts so we can
 * stop requesting dataSync FGS before / after exhaustion and fall back to WorkManager + FCM.
 *
 * Impossible to fix: raising the 6h/24h platform cap. Mitigation only.
 */
object DataSyncFgsQuotaTracker {

    private const val TAG = "DataSyncFgsQuota"
    private const val PREFS = "data_sync_fgs_quota"
    private const val KEY_DAY_EPOCH = "day_epoch"
    private const val KEY_USED_MS = "used_ms"
    private const val KEY_TIMEOUT_COUNT = "timeout_count"
    private const val KEY_BURST_START_MS = "burst_start_ms"
    private const val KEY_LAST_TIMEOUT_AT_MS = "last_timeout_at_ms"
    private const val KEY_EXHAUSTED = "exhausted"

    /** Soft warn threshold — prefer WorkManager for non-urgent wakes. */
    private const val NEAR_QUOTA_RATIO = 0.80

    /** After OS timeout, treat as exhausted until next UTC day rollover (or explicit clear). */
    private const val EXHAUSTION_HOLD_MS = 6 * 60 * 60 * 1000L

    @JvmStatic
    fun dailyCapMs(): Long =
        TimeUnit.HOURS.toMillis(AndroidPlatform.dataSyncFgsDailyCapHours().toLong())

    @JvmStatic
    fun usedMsToday(context: Context): Long {
        rolloverIfNeeded(context)
        return prefs(context).getLong(KEY_USED_MS, 0L)
    }

    @JvmStatic
    fun remainingMs(context: Context): Long =
        (dailyCapMs() - usedMsToday(context)).coerceAtLeast(0L)

    @JvmStatic
    fun usageRatio(context: Context): Double {
        val cap = dailyCapMs().toDouble()
        if (cap <= 0) return 0.0
        return (usedMsToday(context) / cap).coerceIn(0.0, 1.0)
    }

    @JvmStatic
    fun isNearQuota(context: Context): Boolean = usageRatio(context) >= NEAR_QUOTA_RATIO

    @JvmStatic
    fun isExhausted(context: Context): Boolean {
        rolloverIfNeeded(context)
        val p = prefs(context)
        if (p.getBoolean(KEY_EXHAUSTED, false)) {
            val last = p.getLong(KEY_LAST_TIMEOUT_AT_MS, 0L)
            if (last > 0L && System.currentTimeMillis() - last < EXHAUSTION_HOLD_MS) {
                return true
            }
            // Hold expired within same day — still respect soft remaining==0.
        }
        return remainingMs(context) <= 0L
    }

    @JvmStatic
    fun timeoutCountToday(context: Context): Int {
        rolloverIfNeeded(context)
        return prefs(context).getInt(KEY_TIMEOUT_COUNT, 0)
    }

    @JvmStatic
    fun onBurstStarted(context: Context) {
        if (!AndroidPlatform.useBurstFgs()) return
        rolloverIfNeeded(context)
        prefs(context).edit()
            .putLong(KEY_BURST_START_MS, System.currentTimeMillis())
            .apply()
    }

    @JvmStatic
    fun onBurstEnded(context: Context, reason: String) {
        if (!AndroidPlatform.useBurstFgs()) return
        rolloverIfNeeded(context)
        val p = prefs(context)
        val start = p.getLong(KEY_BURST_START_MS, 0L)
        if (start <= 0L) return
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(0L)
        val used = p.getLong(KEY_USED_MS, 0L) + elapsed
        p.edit()
            .putLong(KEY_USED_MS, used)
            .putLong(KEY_BURST_START_MS, 0L)
            .apply()
        Log.i(
            TAG,
            "burst accounted reason=$reason elapsedMs=$elapsed usedMs=$used " +
                "capMs=${dailyCapMs()} ratio=${"%.2f".format(usageRatio(context))}",
        )
    }

    @JvmStatic
    fun onTimeout(context: Context) {
        if (!AndroidPlatform.useBurstFgs()) return
        rolloverIfNeeded(context)
        onBurstEnded(context, "fgs_timeout")
        val p = prefs(context)
        val count = p.getInt(KEY_TIMEOUT_COUNT, 0) + 1
        p.edit()
            .putInt(KEY_TIMEOUT_COUNT, count)
            .putBoolean(KEY_EXHAUSTED, true)
            .putLong(KEY_LAST_TIMEOUT_AT_MS, System.currentTimeMillis())
            .apply()
        Log.w(TAG, "quota/timeout recorded count=$count exhausted=true")
    }

    @JvmStatic
    fun clearExhaustionForTests(context: Context) {
        prefs(context).edit().clear().apply()
    }

    @JvmStatic
    fun snapshot(context: Context): Map<String, Any> {
        rolloverIfNeeded(context)
        return linkedMapOf(
            "dataSyncFgsDailyCapMs" to dailyCapMs(),
            "dataSyncFgsUsedMs" to usedMsToday(context),
            "dataSyncFgsRemainingMs" to remainingMs(context),
            "dataSyncFgsUsageRatio" to usageRatio(context),
            "dataSyncFgsNearQuota" to isNearQuota(context),
            "dataSyncFgsExhausted" to isExhausted(context),
            "dataSyncFgsTimeoutCountToday" to timeoutCountToday(context),
        )
    }

    private fun rolloverIfNeeded(context: Context) {
        val day = currentDayEpoch()
        val p = prefs(context)
        if (p.getLong(KEY_DAY_EPOCH, -1L) == day) return
        p.edit()
            .putLong(KEY_DAY_EPOCH, day)
            .putLong(KEY_USED_MS, 0L)
            .putInt(KEY_TIMEOUT_COUNT, 0)
            .putBoolean(KEY_EXHAUSTED, false)
            .putLong(KEY_BURST_START_MS, 0L)
            .putLong(KEY_LAST_TIMEOUT_AT_MS, 0L)
            .apply()
        Log.i(TAG, "quota day rollover dayEpoch=$day")
    }

    /** UTC day ordinal — aligns loosely with OS daily quota windows. */
    private fun currentDayEpoch(): Long =
        System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
