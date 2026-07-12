package com.childmonitor.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureFrameLoggerTest {

    @Test
    fun hasReceivedFrame_falseUntilFirstFrame() {
        CaptureFrameLogger.resetFrameCount()
        assertFalse(CaptureFrameLogger.hasReceivedFrame())
        CaptureFrameLogger.onFrameCaptured(CaptureFrameLogger.VideoFrameDimensions(720, 1280))
        assertTrue(CaptureFrameLogger.hasReceivedFrame())
        assertTrue(CaptureFrameLogger.capturedFrameCount() >= 1L)
    }
}
