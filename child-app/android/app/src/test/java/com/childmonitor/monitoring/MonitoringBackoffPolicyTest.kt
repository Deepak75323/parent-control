package com.childmonitor.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringBackoffPolicyTest {

    @Test
    fun computeDelayMs_exponentialGrowth() {
        assertDelayNear(60_000L, MonitoringBackoffPolicy.computeDelayMs(1))
        assertDelayNear(120_000L, MonitoringBackoffPolicy.computeDelayMs(2))
        assertDelayNear(240_000L, MonitoringBackoffPolicy.computeDelayMs(3))
    }

    private fun assertDelayNear(expected: Long, actual: Long) {
        val margin = (expected * 0.15).toLong()
        assertTrue(actual in (expected - margin)..(expected + margin))
    }

    @Test
    fun computeDelayMs_capsAtOneHour() {
        val delay = MonitoringBackoffPolicy.computeDelayMs(10)
        assertTrue(delay <= 3_960_000L) // max + jitter headroom
        assertTrue(delay >= 3_600_000L)
    }

    @Test
    fun computeDelayMs_zeroForNoFailures() {
        assertEquals(0L, MonitoringBackoffPolicy.computeDelayMs(0))
    }
}
