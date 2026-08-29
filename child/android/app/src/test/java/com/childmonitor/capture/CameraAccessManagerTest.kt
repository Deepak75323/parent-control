package com.childmonitor.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraAccessManagerTest {

    @Test
    fun shouldSkipDisplacementProbe_whenCameraFreeOrUnknown() {
        assertTrue(CameraAccessManager.shouldSkipDisplacementProbe(true))
        assertTrue(CameraAccessManager.shouldSkipDisplacementProbe(null))
    }

    @Test
    fun shouldNotSkipDisplacementProbe_whenCameraInUse() {
        assertFalse(CameraAccessManager.shouldSkipDisplacementProbe(false))
    }
}
