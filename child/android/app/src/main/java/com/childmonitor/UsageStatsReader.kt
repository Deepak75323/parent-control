package com.childmonitor

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class UsageEntry(val packageName: String, val usageMs: Long, val recordedAt: Instant)

/**
 * Reads per-app foreground time for a wall-clock "today" window.
 *
 * Strategy (matches Digital Wellbeing / MIUI App activity more closely):
 * 1. Prefer **today's INTERVAL_DAILY bucket** per package (no cross-day `.add()`).
 * 2. Also compute **event sessions**; per package take `max(daily, events)` so we don't
 *    under-count when MIUI drops events, and don't over-count by merging yesterday.
 */
object UsageStatsReader {
    private const val TAG = "UsageStatsReader"

    /** Cap only truly stuck sessions (missing PAUSE after an app switch). */
    private const val MAX_OPEN_SESSION_MS = 4L * 60L * 60L * 1000L

    /** System overlays that briefly RESUME without the underlying app pausing. */
    private fun isOverlayPackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p == "android" ||
            p.contains("systemui") ||
            p.contains("permissioncontroller") ||
            p.startsWith("com.android.intentresolver") ||
            p.startsWith("com.miui.freeform") ||
            p.startsWith("com.miui.fliphome")
    }

    fun readSince(context: Context, sinceMs: Long): List<UsageEntry> {
        val end = System.currentTimeMillis()
        val start = if (sinceMs > 0) sinceMs else end - 24 * 60 * 60 * 1000
        return readRange(context, start, end)
    }

    /** Per-app usage for an explicit wall-clock window [[startMs], [endMs]). */
    fun readRange(context: Context, startMs: Long, endMs: Long): List<UsageEntry> {
        if (endMs <= startMs) return emptyList()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val totals = mergeDailyAndEvents(manager, startMs, endMs)
        // Keep stamp on the usage calendar day (end of window), unique per sync so parent
        // newest-first can prefer the latest ingest over older inflated rows.
        val recordedAt = Instant.ofEpochMilli(endMs - 1)
        return totals.entries
            .filter { it.value > 0L }
            .map { UsageEntry(it.key, it.value, recordedAt) }
            .sortedByDescending { it.usageMs }
    }

    /** Millisecond epoch of midnight (start of today) in the device's local timezone. */
    fun startOfTodayMs(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * True when PACKAGE_USAGE_STATS / AppOps GET_USAGE_STATS is granted.
     *
     * Do **not** use a short [UsageStatsManager.queryUsageStats] probe — on MIUI/HyperOS that
     * often returns an empty list even when the op is allowed.
     */
    fun hasPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    )
                }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Per-package foreground sessions inside [[startMs], [endMs]).
     *
     * Important: do **not** end package A's session when package B resumes — MIUI system
     * overlays briefly resume without pausing YouTube, which under-counted vs Digital Wellbeing.
     * End A's session only on A's own PAUSE/STOPPED or on screen lock.
     */
    internal fun accumulateFromEvents(
        events: List<UsageEventSample>,
        startMs: Long,
        endMs: Long,
        nowMs: Long = endMs,
    ): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val openStart = HashMap<String, Long>()
        val seenResume = HashSet<String>()
        var screenInteractive = true

        fun addRange(pkg: String, fromRaw: Long, toRaw: Long) {
            val from = maxOf(fromRaw, startMs)
            val to = minOf(toRaw, endMs)
            if (to > from) {
                totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            }
        }

        fun closePackage(pkg: String, atMs: Long) {
            val started = openStart.remove(pkg) ?: return
            if (screenInteractive) {
                addRange(pkg, started, atMs)
            }
        }

        fun closeAll(atMs: Long) {
            val pkgs = openStart.keys.toList()
            for (pkg in pkgs) closePackage(pkg, atMs)
        }

        for (event in events) {
            val t = event.timeStamp
            if (t < startMs || t > endMs) continue
            when (event.eventType) {
                UsageEvents.Event.KEYGUARD_SHOWN,
                -> {
                    closeAll(t)
                    screenInteractive = false
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                -> {
                    // Do not end app sessions here — on MIUI this can fire during video /
                    // Doze without a real lock and under-counts YouTube vs Digital Wellbeing.
                    // KEYGUARD_SHOWN is the reliable "stop counting" signal.
                }
                UsageEvents.Event.KEYGUARD_HIDDEN,
                UsageEvents.Event.SCREEN_INTERACTIVE,
                -> {
                    val was = screenInteractive
                    screenInteractive = true
                    // Unlock after RESUME-before-unlock: start counting open apps from now.
                    if (!was) {
                        for (pkg in openStart.keys.toList()) {
                            openStart[pkg] = t
                        }
                    }
                }
                UsageEvents.Event.ACTIVITY_RESUMED,
                -> {
                    val pkg = event.packageName ?: continue
                    seenResume.add(pkg)
                    // Real app switch (not a system overlay): close other user apps still open.
                    if (!isOverlayPackage(pkg)) {
                        for (other in openStart.keys.toList()) {
                            if (other != pkg && !isOverlayPackage(other)) {
                                closePackage(other, t)
                            }
                        }
                    }
                    if (!openStart.containsKey(pkg)) {
                        openStart[pkg] = t
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    val pkg = event.packageName ?: continue
                    if (openStart.containsKey(pkg)) {
                        closePackage(pkg, t)
                    } else if (!seenResume.contains(pkg) && screenInteractive && t > startMs) {
                        // Crossed midnight already in foreground — first event is a pause.
                        addRange(pkg, startMs, t)
                        seenResume.add(pkg)
                    }
                }
            }
        }

        // Still open at end of window.
        if (screenInteractive) {
            for ((pkg, started) in openStart) {
                val openFor = nowMs - started
                val cappedEnd =
                    if (openFor > MAX_OPEN_SESSION_MS) started + MAX_OPEN_SESSION_MS else nowMs
                addRange(pkg, started, minOf(cappedEnd, endMs))
            }
        }
        return totals
    }

    /**
     * Attribute INTERVAL_DAILY buckets to [[startMs], [endMs]).
     *
     * MIUI daily buckets are offset from midnight (e.g. 1:03pm → next midnight). Morning
     * usage sits in the previous bucket; afternoon in the current one.
     *
     * - Buckets **fully inside** the window: use full [totalTimeVisible] (sum abutting).
     * - Buckets that **straddle** midnight: skip here — proportional attribution over-counts
     *   when evening usage dominates the previous bucket. [mergeDailyAndEvents] fills the
     *   straddle with event sessions instead.
     */
    internal fun dailyBucketTotals(
        stats: List<UsageStats>,
        startMs: Long,
        endMs: Long,
        sumAbutting: Boolean = true,
        includeStraddling: Boolean = false,
    ): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val bestByKey = HashMap<Pair<String, Long>, UsageStats>()
        for (stat in stats) {
            val pkg = stat.packageName ?: continue
            val key = pkg to stat.firstTimeStamp
            val prev = bestByKey[key]
            if (prev == null || visibleMs(stat) > visibleMs(prev)) {
                bestByKey[key] = stat
            }
        }

        for (stat in bestByKey.values) {
            val pkg = stat.packageName ?: continue
            val visible = visibleMs(stat)
            if (visible <= 0L) continue

            val bucketStart = stat.firstTimeStamp
            var bucketEnd = stat.lastTimeStamp
            if (bucketEnd <= bucketStart) {
                bucketEnd = bucketStart + 86_400_000L
            }
            val overlapStart = maxOf(bucketStart, startMs)
            val overlapEnd = minOf(bucketEnd, endMs)
            if (overlapEnd <= overlapStart) continue

            // Skip buckets that began before the window (MIUI previous-day offset bucket).
            // Proportional attribution of those over-counts evening usage as "this morning".
            if (bucketStart < startMs && !includeStraddling) {
                continue
            }

            val bucketDur = bucketEnd - bucketStart
            val attributed = visible * (overlapEnd - overlapStart) / bucketDur
            if (attributed <= 0L) continue
            totals[pkg] =
                if (sumAbutting) {
                    (totals[pkg] ?: 0L) + attributed
                } else {
                    maxOf(totals[pkg] ?: 0L, attributed)
                }
        }
        return totals
    }

    private fun visibleMs(stat: UsageStats): Long {
        val fg = stat.totalTimeInForeground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                stat.totalTimeVisible.takeIf { it > 0L } ?: fg
            } catch (_: Throwable) {
                fg
            }
        }
        return fg
    }

    /**
     * Earliest begin timestamp among daily buckets that start inside [[startMs], [endMs]).
     * Used as the MIUI "day anchor" (often ~1pm) so morning can be filled from events.
     */
    internal fun earliestBucketStartOnOrAfter(
        stats: List<UsageStats>,
        startMs: Long,
        endMs: Long,
    ): Long? {
        var minStart: Long? = null
        for (stat in stats) {
            val bs = stat.firstTimeStamp
            if (bs in startMs until endMs) {
                minStart = minOf(minStart ?: bs, bs)
            }
        }
        return minStart
    }

    /**
     * Merge DAILY (afternoon+ fully-inside buckets), morning event sessions up to the MIUI
     * daily anchor, full-day events, and BEST (max-only) — take the max per package.
     */
    private fun mergeDailyAndEvents(
        manager: UsageStatsManager,
        startMs: Long,
        endMs: Long,
    ): Map<String, Long> {
        val dailyQueryStart = startMs - 86_400_000L
        val rawDaily =
            try {
                manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dailyQueryStart, endMs)
            } catch (e: Exception) {
                Log.w(TAG, "INTERVAL_DAILY query failed", e)
                emptyList()
            }

        val dailyInside =
            dailyBucketTotals(
                rawDaily,
                startMs,
                endMs,
                sumAbutting = true,
                includeStraddling = false,
            )

        val anchorMs = earliestBucketStartOnOrAfter(rawDaily, startMs, endMs) ?: endMs

        val eventsFull =
            try {
                queryEventTotals(manager, startMs, endMs)
            } catch (e: Exception) {
                Log.w(TAG, "queryEvents failed", e)
                emptyMap()
            }

        // Morning slice: midnight → MIUI daily anchor (events only; no proportional).
        val eventsMorning =
            if (anchorMs > startMs + 60_000L) {
                try {
                    queryEventTotals(manager, startMs, anchorMs)
                } catch (e: Exception) {
                    Log.w(TAG, "queryEvents morning failed", e)
                    emptyMap()
                }
            } else {
                emptyMap()
            }

        // Soft-fill morning: allow proportional straddling to close small gaps vs Digital
        // Wellbeing, but cap the boost so evening-heavy previous buckets can't inflate.
        val straddlingOnly =
            dailyBucketTotals(
                rawDaily.filter { it.firstTimeStamp < startMs },
                startMs,
                endMs,
                sumAbutting = true,
                includeStraddling = true,
            )

        val morning = HashMap<String, Long>()
        for (pkg in eventsMorning.keys + straddlingOnly.keys) {
            val ev = eventsMorning[pkg] ?: 0L
            val prop = straddlingOnly[pkg] ?: 0L
            morning[pkg] =
                when {
                    ev <= 0L -> prop
                    prop <= ev -> ev
                    else -> minOf(prop, ev + 12L * 60L * 1000L) // max +12m toward DW
                }
        }

        val morningPlusAfternoon = HashMap<String, Long>()
        val comboKeys = dailyInside.keys + morning.keys
        for (pkg in comboKeys) {
            morningPlusAfternoon[pkg] = (morning[pkg] ?: 0L) + (dailyInside[pkg] ?: 0L)
        }

        val best =
            try {
                dailyBucketTotals(
                    manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, dailyQueryStart, endMs),
                    startMs,
                    endMs,
                    sumAbutting = false,
                    includeStraddling = true,
                )
            } catch (e: Exception) {
                Log.w(TAG, "INTERVAL_BEST query failed", e)
                emptyMap()
            }

        val merged = HashMap<String, Long>()
        val keys = dailyInside.keys + morningPlusAfternoon.keys + eventsFull.keys + best.keys
        for (pkg in keys) {
            merged[pkg] =
                maxOf(
                    dailyInside[pkg] ?: 0L,
                    morningPlusAfternoon[pkg] ?: 0L,
                    eventsFull[pkg] ?: 0L,
                    best[pkg] ?: 0L,
                )
        }
        return merged
    }

    private fun queryEventTotals(
        manager: UsageStatsManager,
        startMs: Long,
        endMs: Long,
    ): Map<String, Long> {
        val usageEvents = manager.queryEvents(startMs, endMs)
        val samples = ArrayList<UsageEventSample>(512)
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            samples.add(
                UsageEventSample(
                    packageName = event.packageName,
                    eventType = event.eventType,
                    timeStamp = event.timeStamp,
                ),
            )
        }
        return accumulateFromEvents(samples, startMs, endMs, nowMs = endMs)
    }
}

/** Lightweight event for tests / offline accumulation. */
internal data class UsageEventSample(
    val packageName: String?,
    val eventType: Int,
    val timeStamp: Long,
)
