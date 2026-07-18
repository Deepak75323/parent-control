package com.childmonitor.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StompReconnectPolicyTest {

    @Test
    fun delayMs_growsExponentiallyBeforeCap() {
        val fixed = Random(0)
        val d1 = StompReconnectPolicy.delayMs(1, fixed)
        val d2 = StompReconnectPolicy.delayMs(2, fixed)
        val d3 = StompReconnectPolicy.delayMs(3, fixed)
        assertTrue("attempt1=$d1", d1 in 1_600L..2_400L)
        assertTrue("attempt2=$d2", d2 in 3_200L..4_800L)
        assertTrue("attempt3=$d3", d3 in 6_400L..9_600L)
    }

    @Test
    fun delayMs_capsAroundOneMinute() {
        val delay = StompReconnectPolicy.delayMs(20, Random(1))
        assertTrue(delay in 48_000L..72_000L)
    }

    @Test
    fun maxAttempts_isBounded() {
        assertTrue(StompReconnectPolicy.MAX_ATTEMPTS in 10..100)
    }
}
