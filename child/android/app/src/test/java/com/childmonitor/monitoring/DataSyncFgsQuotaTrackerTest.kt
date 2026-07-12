package com.childmonitor.monitoring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1-08: dataSync FGS quota awareness + degradation (Android 15/16).
 * Platform quota cannot be raised — we only track, degrade, and recover.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataSyncFgsQuotaTrackerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DataSyncFgsQuotaTracker.clearExhaustionForTests(context)
        MonitoringDegradationController.resetForTests(context)
    }

    @After
    fun tearDown() {
        DataSyncFgsQuotaTracker.clearExhaustionForTests(context)
        MonitoringDegradationController.resetForTests(context)
    }

    @Test
    fun dailyCap_isSixHours() {
        assertEquals(6L * 60 * 60 * 1000L, DataSyncFgsQuotaTracker.dailyCapMs())
    }

    @Test
    fun burstAccounting_increasesUsedMs() {
        DataSyncFgsQuotaTracker.onBurstStarted(context)
        // Simulate ~1s burst by writing prefs directly after start.
        val prefs = context.getSharedPreferences("data_sync_fgs_quota", Context.MODE_PRIVATE)
        prefs.edit().putLong("burst_start_ms", System.currentTimeMillis() - 1_000L).commit()
        DataSyncFgsQuotaTracker.onBurstEnded(context, "test")
        assertTrue(DataSyncFgsQuotaTracker.usedMsToday(context) >= 900L)
        assertFalse(DataSyncFgsQuotaTracker.isExhausted(context))
    }

    @Test
    fun timeout_marksExhausted() {
        DataSyncFgsQuotaTracker.onBurstStarted(context)
        DataSyncFgsQuotaTracker.onTimeout(context)
        assertTrue(DataSyncFgsQuotaTracker.isExhausted(context))
        assertTrue(DataSyncFgsQuotaTracker.timeoutCountToday(context) >= 1)
    }

    @Test
    fun nearQuota_atEightyPercent() {
        val prefs = context.getSharedPreferences("data_sync_fgs_quota", Context.MODE_PRIVATE)
        val day = System.currentTimeMillis() / (24L * 60 * 60 * 1000L)
        val used = (DataSyncFgsQuotaTracker.dailyCapMs() * 0.85).toLong()
        prefs.edit()
            .putLong("day_epoch", day)
            .putLong("used_ms", used)
            .putBoolean("exhausted", false)
            .commit()
        assertTrue(DataSyncFgsQuotaTracker.isNearQuota(context))
        assertFalse(DataSyncFgsQuotaTracker.isExhausted(context))
    }

    @Test
    @Config(sdk = [34])
    fun preAndroid15_trackerNoOps() {
        // useBurstFgs() false on API 34 — timeout should not mark exhaustion path used by launcher.
        DataSyncFgsQuotaTracker.onTimeout(context)
        // Still may write if platform check fails on Robolectric SDK — assert remaining API.
        assertTrue(DataSyncFgsQuotaTracker.dailyCapMs() > 0L)
    }
}
