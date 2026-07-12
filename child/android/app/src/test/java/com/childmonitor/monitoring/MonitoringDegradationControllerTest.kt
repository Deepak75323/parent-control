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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MonitoringDegradationControllerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MonitoringDegradationController.resetForTests(context)
        DataSyncFgsQuotaTracker.clearExhaustionForTests(context)
    }

    @After
    fun tearDown() {
        MonitoringDegradationController.resetForTests(context)
        DataSyncFgsQuotaTracker.clearExhaustionForTests(context)
    }

    @Test
    fun healthy_defaultsToNormal() {
        assertEquals(MonitoringMode.NORMAL, MonitoringDegradationController.evaluate(context))
        assertFalse(MonitoringDegradationController.isDegraded(context))
        assertFalse(MonitoringDegradationController.shouldSkipBurstFgs(context, forceUrgent = false))
    }

    @Test
    fun timeout_entersWorkManagerOnly() {
        MonitoringDegradationController.onFgsTimeout(context)
        assertEquals(MonitoringMode.WORKMANAGER_ONLY, MonitoringDegradationController.currentMode(context))
        assertTrue(MonitoringDegradationController.isDegraded(context))
        assertTrue(MonitoringDegradationController.shouldSkipBurstFgs(context, forceUrgent = true))
        assertTrue(MonitoringDegradationController.shouldSkipBurstFgs(context, forceUrgent = false))
    }

    @Test
    fun nearQuota_skipsNonUrgentBursts() {
        val prefs = context.getSharedPreferences("data_sync_fgs_quota", Context.MODE_PRIVATE)
        val day = System.currentTimeMillis() / (24L * 60 * 60 * 1000L)
        prefs.edit()
            .putLong("day_epoch", day)
            .putLong("used_ms", (DataSyncFgsQuotaTracker.dailyCapMs() * 0.9).toLong())
            .putBoolean("exhausted", false)
            .commit()

        assertEquals(MonitoringMode.CONSERVATIVE, MonitoringDegradationController.evaluate(context))
        assertTrue(MonitoringDegradationController.shouldSkipBurstFgs(context, forceUrgent = false))
        assertFalse(MonitoringDegradationController.shouldSkipBurstFgs(context, forceUrgent = true))
    }

    @Test
    fun heartbeatFields_includeQuotaAndMode() {
        MonitoringDegradationController.onFgsTimeout(context)
        val fields = MonitoringDegradationController.heartbeatFields(context)
        assertEquals(MonitoringMode.WORKMANAGER_ONLY.name, fields["monitoringMode"])
        assertEquals(true, fields["monitoringDegraded"])
        assertTrue(fields.containsKey("dataSyncFgsDailyCapMs"))
        assertTrue(fields.containsKey("standbyBucket"))
    }

    @Test
    fun android16_sameDegradationContract() {
        // Same UPSIDE_DOWN_CAKE+/VANILLA_ICE_CREAM contract as API 35 — quota cannot be raised.
        assertTrue(MonitoringMode.WORKMANAGER_ONLY.isDegraded())
        assertTrue(MonitoringMode.STANDBY_DEGRADED.isDegraded())
        assertTrue(MonitoringMode.CONSERVATIVE.isDegraded())
        assertFalse(MonitoringMode.NORMAL.isDegraded())
    }

    @Test
    fun standbyBucketReader_sparseDetection() {
        assertTrue(StandbyBucketReader.isSparseScheduling(StandbyBucketReader.RARE))
        assertTrue(StandbyBucketReader.isSparseScheduling(StandbyBucketReader.RESTRICTED))
        assertFalse(StandbyBucketReader.isSparseScheduling(StandbyBucketReader.ACTIVE))
        assertFalse(StandbyBucketReader.isSparseScheduling(StandbyBucketReader.FREQUENT))
    }
}
