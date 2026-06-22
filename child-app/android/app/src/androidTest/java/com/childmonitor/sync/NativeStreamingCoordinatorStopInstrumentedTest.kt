package com.childmonitor.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.childmonitor.capture.AudioStreamForegroundService
import com.childmonitor.capture.CameraStreamForegroundService
import com.childmonitor.capture.StreamCaptureTeardown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method

/**
 * Verifies native coordinator stop delegates to [StreamCaptureTeardown] (P0-3).
 */
@RunWith(AndroidJUnit4::class)
class NativeStreamingCoordinatorStopInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    }

    @Test
    fun stopAllStreams_invokesStreamCaptureTeardown() {
        CameraStreamForegroundService.start(context)
        AudioStreamForegroundService.start(context)

        val before = StreamCaptureTeardown.invocationCount()
        invokeStopAllStreams(context, "instrumented parent stop")

        assertEquals(before + 1, StreamCaptureTeardown.invocationCount())
        assertEquals("instrumented parent stop", StreamCaptureTeardown.lastReason)
        assertFalse(CameraStreamForegroundService.isActive())
        assertFalse(AudioStreamForegroundService.isActive())
    }

    private fun invokeStopAllStreams(context: Context, reason: String) {
        val method: Method = NativeStreamingCoordinator::class.java.getDeclaredMethod(
            "stopAllStreams",
            Context::class.java,
            String::class.java,
        )
        method.isAccessible = true
        method.invoke(NativeStreamingCoordinator, context, reason)
    }
}
