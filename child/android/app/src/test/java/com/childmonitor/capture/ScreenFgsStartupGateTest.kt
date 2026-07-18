package com.childmonitor.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenFgsStartupGateTest {

    @Before
    fun resetGate() {
        ScreenFgsStartupGate.resetForTests()
    }

    @Test
    fun startsClosed() {
        assertFalse(ScreenFgsStartupGate.canStartScreenFgs())
    }

    @Test
    fun markReadyNow_opensGate() {
        assertFalse(ScreenFgsStartupGate.canStartScreenFgs())
        ScreenFgsStartupGate.markReadyNow()
        assertTrue(ScreenFgsStartupGate.canStartScreenFgs())
    }
}
