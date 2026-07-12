package com.childmonitor.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM unit tests — no Robolectric (API 36 not yet in Robolectric). */
class Android16PlatformTest {

    @Test
    fun sdk36_constants() {
        assertEquals(36, AndroidPlatform.SDK_ANDROID_16)
    }

    @Test
    fun requiredFgsTypes_includesCaptureTypes() {
        val types = Android16ReadinessEvaluator.REQUIRED_FGS_TYPES
        assertTrue(types.isNotEmpty())
    }

    @Test
    fun screenFgsDelay_api36Threshold_configured() {
        assertTrue(AndroidPlatform.screenFgsColdStartDelayMs() >= 3_000L)
    }
}
