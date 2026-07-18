package com.childmonitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageStatsReaderTest {

    @Test
    fun accumulateFromEvents_sumsForegroundSessionsWithinWindow() {
        val start = 1_000_000L
        val end = start + 3_600_000L
        val events =
            listOf(
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_RESUMED, start + 60_000),
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_PAUSED, start + 600_000),
                UsageEventSample("com.android.chrome", UsageEvents.Event.ACTIVITY_RESUMED, start + 700_000),
                UsageEventSample("com.android.chrome", UsageEvents.Event.ACTIVITY_PAUSED, start + 800_000),
            )

        val totals = UsageStatsReader.accumulateFromEvents(events, start, end)

        assertEquals(540_000L, totals["com.google.android.youtube"])
        assertEquals(100_000L, totals["com.android.chrome"])
    }

    @Test
    fun accumulateFromEvents_keepsCountingThroughSystemOverlayResume() {
        val start = 1_000_000L
        val end = start + 3_600_000L
        val events =
            listOf(
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_RESUMED, start + 60_000),
                // MIUI overlay briefly resumes — must NOT end YouTube
                UsageEventSample("miui.systemui.plugin", UsageEvents.Event.ACTIVITY_RESUMED, start + 120_000),
                UsageEventSample("miui.systemui.plugin", UsageEvents.Event.ACTIVITY_PAUSED, start + 130_000),
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_PAUSED, start + 600_000),
            )

        val totals = UsageStatsReader.accumulateFromEvents(events, start, end)

        assertEquals(540_000L, totals["com.google.android.youtube"])
    }

    @Test
    fun accumulateFromEvents_stopsCountingWhenScreenLocks() {
        val start = 1_000_000L
        val end = start + 3_600_000L
        val events =
            listOf(
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_RESUMED, start + 60_000),
                UsageEventSample(null, UsageEvents.Event.KEYGUARD_SHOWN, start + 120_000),
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_PAUSED, start + 3_000_000),
            )

        val totals = UsageStatsReader.accumulateFromEvents(events, start, end)

        assertEquals(60_000L, totals["com.google.android.youtube"])
    }

    @Test
    fun accumulateFromEvents_startsClockOnUnlockAfterResumeWhileLocked() {
        val start = 1_000_000L
        val end = start + 3_600_000L
        val events =
            listOf(
                UsageEventSample(null, UsageEvents.Event.KEYGUARD_SHOWN, start + 10_000),
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_RESUMED, start + 20_000),
                UsageEventSample(null, UsageEvents.Event.KEYGUARD_HIDDEN, start + 30_000),
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_PAUSED, start + 90_000),
            )

        val totals = UsageStatsReader.accumulateFromEvents(events, start, end)

        // Count only unlock→pause (60s), not resume-while-locked.
        assertEquals(60_000L, totals["com.google.android.youtube"])
    }

    @Test
    fun accumulateFromEvents_countsPreMidnightForegroundUntilFirstPause() {
        val start = 1_000_000L
        val end = start + 3_600_000L
        val events =
            listOf(
                UsageEventSample("com.whatsapp", UsageEvents.Event.ACTIVITY_PAUSED, start + 180_000),
            )

        val totals = UsageStatsReader.accumulateFromEvents(events, start, end)

        assertEquals(180_000L, totals["com.whatsapp"])
    }

    @Test
    fun accumulateFromEvents_ignoresOrphanPauseAfterNormalSession() {
        val start = 1_000_000L
        val end = start + 3_600_000L
        val events =
            listOf(
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_RESUMED, start + 60_000),
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_PAUSED, start + 120_000),
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_PAUSED, start + 3_000_000),
            )

        val totals = UsageStatsReader.accumulateFromEvents(events, start, end)

        assertEquals(60_000L, totals["com.google.android.youtube"])
    }

    @Test
    fun accumulateFromEvents_closesPreviousAppOnRealAppSwitch() {
        val start = 1_000_000L
        val end = start + 3_600_000L
        val events =
            listOf(
                UsageEventSample("com.google.android.youtube", UsageEvents.Event.ACTIVITY_RESUMED, start + 60_000),
                UsageEventSample("com.android.chrome", UsageEvents.Event.ACTIVITY_RESUMED, start + 600_000),
                UsageEventSample("com.android.chrome", UsageEvents.Event.ACTIVITY_PAUSED, start + 800_000),
            )

        val totals = UsageStatsReader.accumulateFromEvents(events, start, end)

        assertEquals(540_000L, totals["com.google.android.youtube"])
        assertEquals(200_000L, totals["com.android.chrome"])
    }

    @Test
    fun dailyBucketTotals_attributesOverlapAcrossMidnightBoundary() {
        val start = 1_000_000L
        val end = start + 86_400_000L
        // Bucket started 12h before midnight, lasts 24h, 134 min visible total.
        val spanning =
            usageStats(
                "com.google.android.youtube",
                totalFg = 102 * 60_000L,
                first = start - 43_200_000L,
                last = start - 43_200_000L + 86_400_000L,
                lastUsed = start + 40_000_000L,
            )
        setField(spanning, "mTotalTimeVisible", 134 * 60_000L)

        val totals =
            UsageStatsReader.dailyBucketTotals(
                listOf(spanning),
                start,
                end,
                includeStraddling = true,
            )

        // Half the bucket overlaps today → ~67 min of visible time.
        assertEquals(67 * 60_000L, totals["com.google.android.youtube"])
    }

    @Test
    fun dailyBucketTotals_skipsStraddlingBucketsByDefault() {
        val start = 1_000_000L
        val end = start + 86_400_000L
        val spanning =
            usageStats(
                "com.google.android.youtube",
                totalFg = 100 * 60_000L,
                first = start - 43_200_000L,
                last = start - 43_200_000L + 86_400_000L,
                lastUsed = start + 1,
            )

        val totals = UsageStatsReader.dailyBucketTotals(listOf(spanning), start, end)

        assertEquals(null, totals["com.google.android.youtube"])
    }

    @Test
    fun dailyBucketTotals_usesFullBucketWhenEntirelyToday() {
        val start = 1_000_000L
        val end = start + 86_400_000L
        val today =
            usageStats(
                "com.discord",
                totalFg = 29 * 60_000L,
                first = start + 3_600_000L,
                last = start + 3_600_000L + 3_600_000L,
                lastUsed = start + 5_000_000L,
            )

        val totals = UsageStatsReader.dailyBucketTotals(listOf(today), start, end)

        assertEquals(29 * 60_000L, totals["com.discord"])
    }

    @Test
    fun dailyBucketTotals_keepsAfternoonBucketAndIgnoresMorningStraddle() {
        val start = 1_000_000L
        val end = start + 86_400_000L
        val noonOffset = 13 * 3_600_000L
        val morningBucket =
            usageStats(
                "com.google.android.youtube",
                totalFg = 100 * 60_000L,
                first = start - (86_400_000L - noonOffset),
                last = start + noonOffset,
                lastUsed = start + 3_600_000L,
            )
        setField(morningBucket, "mTotalTimeVisible", 100 * 60_000L)
        val afternoonBucket =
            usageStats(
                "com.google.android.youtube",
                totalFg = 27 * 60_000L,
                first = start + noonOffset,
                last = end,
                lastUsed = start + 80_000_000L,
            )
        setField(afternoonBucket, "mTotalTimeVisible", 27 * 60_000L)

        val totals =
            UsageStatsReader.dailyBucketTotals(
                listOf(morningBucket, afternoonBucket),
                start,
                end,
                sumAbutting = true,
            )

        // Only the afternoon bucket (starts inside today) is kept.
        assertEquals(27 * 60_000L, totals["com.google.android.youtube"])
    }

    @Test
    fun dailyBucketTotals_maxModeDoesNotSumOverlappingBestBuckets() {
        val start = 1_000_000L
        val end = start + 86_400_000L
        val a =
            usageStats(
                "com.discord",
                totalFg = 40 * 60_000L,
                first = start,
                last = end,
                lastUsed = start + 1,
            )
        val b =
            usageStats(
                "com.discord",
                totalFg = 50 * 60_000L,
                first = start + 1_000L,
                last = end,
                lastUsed = start + 2,
            )

        val totals =
            UsageStatsReader.dailyBucketTotals(listOf(a, b), start, end, sumAbutting = false)

        assertEquals(50 * 60_000L, totals["com.discord"])
    }

    private fun usageStats(
        pkg: String,
        totalFg: Long,
        first: Long,
        last: Long,
        lastUsed: Long,
    ): UsageStats {
        val stats = UsageStats::class.java.getDeclaredConstructor().newInstance()
        setField(stats, "mPackageName", pkg)
        setField(stats, "mTotalTimeInForeground", totalFg)
        setField(stats, "mBeginTimeStamp", first)
        setField(stats, "mEndTimeStamp", last)
        setField(stats, "mLastTimeUsed", lastUsed)
        return stats
    }

    private fun setField(target: Any, name: String, value: Any) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                f.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw IllegalStateException("Field $name not found on ${target.javaClass}")
    }
}
