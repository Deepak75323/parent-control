package com.childmonitor

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class UsageEntry(val packageName: String, val usageMs: Long, val recordedAt: Instant)

object UsageStatsReader {

    fun readSince(context: Context, sinceMs: Long): List<UsageEntry> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = if (sinceMs > 0) sinceMs else end - 24 * 60 * 60 * 1000
        val stats: Map<String, UsageStats> = manager.queryAndAggregateUsageStats(start, end)

        return stats.values
            .filter { it.totalTimeInForeground > 0 }
            .map {
                UsageEntry(
                    packageName = it.packageName,
                    usageMs = it.totalTimeInForeground,
                    recordedAt = Instant.ofEpochMilli(end),
                )
            }
            .sortedByDescending { it.usageMs }
    }

    /** Millisecond epoch of midnight (start of today) in the device's local timezone. */
    fun startOfTodayMs(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun hasPermission(context: Context): Boolean {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 60_000, end)
        return stats != null && stats.isNotEmpty()
    }
}
