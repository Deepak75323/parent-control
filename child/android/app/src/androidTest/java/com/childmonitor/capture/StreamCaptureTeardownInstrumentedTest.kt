package com.childmonitor.capture

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.childmonitor.capture.StreamSessionRegistry.Owner
import com.childmonitor.capture.StreamTeardownCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [StreamCaptureTeardown] — verifies idempotent stop reaches
 * camera/mic FGS and session registry even without an active React stream.
 *
 * Run: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.childmonitor.capture.StreamCaptureTeardownInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class StreamCaptureTeardownInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        StreamTeardownCoordinator.resetForTests()
        StreamSessionRegistry.release(Owner.JS_HIDDEN)
        StreamSessionRegistry.release(Owner.NATIVE_PUBLISHER)
    }

    @Test
    fun stopAll_isIdempotent_whenNoStreamActive() {
        val before = StreamCaptureTeardown.invocationCount()

        StreamCaptureTeardown.stopAll(context, "test-idempotent-1")
        StreamCaptureTeardown.stopAll(context, "test-idempotent-2")

        assertEquals(before + 2, StreamCaptureTeardown.invocationCount())
        assertFalse(CameraStreamForegroundService.isActive())
        assertFalse(AudioStreamForegroundService.isActive())
    }

    @Test
    fun stopAll_releasesStreamSessionRegistry() {
        assertTrue(StreamSessionRegistry.tryAcquire(Owner.JS_HIDDEN, "session-test"))

        StreamCaptureTeardown.stopAll(context, "test-session-release")

        assertEquals(Owner.NONE, StreamSessionRegistry.activeOwner())
    }

    @Test
    fun stopAll_stopsCameraAndAudioForegroundServices() {
        CameraStreamForegroundService.start(context)
        AudioStreamForegroundService.start(context)

        StreamCaptureTeardown.stopAll(context, "test-fgs-stop")

        assertFalse(CameraStreamForegroundService.isActive())
        assertFalse(AudioStreamForegroundService.isActive())
    }

    @Test
    fun stopAll_recordsLastReason() {
        StreamCaptureTeardown.stopAll(context, "test-reason-marker")
        assertEquals("test-reason-marker", StreamCaptureTeardown.lastReason)
    }
}
