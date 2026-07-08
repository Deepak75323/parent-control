package com.childmonitor

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolicyEnforcementEngineTest {

    @Before
    fun setUp() {
        PolicyEnforcementEngine.resetUsageCountersForTests()
    }

    @Test
    fun recordUsage_clearsPreviousDayTotalsAtMidnightRollover() {
        val yesterday = LocalDate.now().minusDays(1)
        PolicyEnforcementEngine.setUsageDayAndMsForTests(yesterday, "com.tiktok", 45 * 60_000L)

        PolicyEnforcementEngine.recordUsage("com.tiktok", 10 * 60_000L)

        assertEquals(10 * 60_000L, PolicyEnforcementEngine.usageMsTodayForTests("com.tiktok"))
    }

    @Test
    fun recordUsage_accumulatesWithinSameCalendarDay() {
        PolicyEnforcementEngine.recordUsage("com.tiktok", 20 * 60_000L)
        PolicyEnforcementEngine.recordUsage("com.tiktok", 15 * 60_000L)

        assertEquals(35 * 60_000L, PolicyEnforcementEngine.usageMsTodayForTests("com.tiktok"))
    }
}
