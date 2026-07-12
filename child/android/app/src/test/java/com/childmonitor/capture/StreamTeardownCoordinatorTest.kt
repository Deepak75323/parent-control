package com.childmonitor.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StreamTeardownCoordinatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        StreamTeardownCoordinator.resetForTests()
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.JS_HIDDEN)
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)
    }

    @Test
    fun requestStopAll_withoutReact_runsNativeTeardownImmediately() {
        StreamTeardownCoordinator.requestStopAll(context, "unit-native-only")

        assertEquals(StreamTeardownCoordinator.State.IDLE, StreamTeardownCoordinator.currentState())
        assertFalse(AudioStreamForegroundService.isActive())
        assertFalse(CameraStreamForegroundService.isActive())
    }

    @Test
    fun duplicateRequest_whileIdle_isIdempotent() {
        StreamTeardownCoordinator.requestStopAll(context, "first")
        StreamTeardownCoordinator.requestStopAll(context, "second")

        assertEquals(StreamTeardownCoordinator.State.IDLE, StreamTeardownCoordinator.currentState())
    }

    @Test
    fun onJsTeardownComplete_whenIdle_isIgnored() {
        StreamTeardownCoordinator.onJsTeardownComplete(context, "orphan")
        assertEquals(StreamTeardownCoordinator.State.IDLE, StreamTeardownCoordinator.currentState())
    }

    @Test
    fun resetForTests_clearsInProgressState() {
        StreamTeardownCoordinator.resetForTests()
        assertFalse(StreamTeardownCoordinator.isInProgress())
    }
}
