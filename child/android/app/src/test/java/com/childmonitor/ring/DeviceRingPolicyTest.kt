package com.childmonitor.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRingPolicyTest {

    @Test
    fun durationIsThirtySeconds() {
        assertEquals(30_000L, DeviceRingPolicy.DURATION_MS)
    }

    @Test
    fun wakeLockTimeoutIncludesMargin() {
        assertEquals(
            DeviceRingPolicy.DURATION_MS + DeviceRingPolicy.WAKE_LOCK_MARGIN_MS,
            DeviceRingPolicy.wakeLockTimeoutMs(),
        )
    }

    @Test
    fun newRingRestartsExistingAlarm() {
        assertTrue(DeviceRingPolicy.shouldRestartIfAlreadyPlaying())
    }

    @Test
    fun stopRingIsIdempotent() {
        assertTrue(DeviceRingPolicy.stopIsIdempotent())
    }
}
