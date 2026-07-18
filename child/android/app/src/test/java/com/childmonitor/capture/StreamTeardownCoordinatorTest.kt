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
    fun abortPendingJsTeardown_cancelsWatchdogWithoutReleasing() {
        // Simulate JS-pending state (as if emitJsStop returned true).
        val field = StreamTeardownCoordinator::class.java.getDeclaredField("state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(null) as java.util.concurrent.atomic.AtomicReference<*>
        // Use public API path: without React, requestStopAll finishes immediately.
        // Force pending via reflection for the abort contract.
        val stateEnum = Class.forName(
            "com.childmonitor.capture.StreamTeardownCoordinator\$State",
        )
        val jsPending = stateEnum.enumConstants!!.first { it.toString() == "JS_PENDING" }
        @Suppress("UNCHECKED_CAST")
        (state as java.util.concurrent.atomic.AtomicReference<Any>).set(jsPending)

        assertTrue(StreamTeardownCoordinator.abortPendingJsTeardown("unit-abort"))
        assertEquals(StreamTeardownCoordinator.State.IDLE, StreamTeardownCoordinator.currentState())
        assertFalse(StreamTeardownCoordinator.abortPendingJsTeardown("unit-abort-again"))
    }

    @Test
    fun requestImmediateStopAll_endsIdle() {
        StreamTeardownCoordinator.requestImmediateStopAll(context, "unit-immediate")
        assertEquals(StreamTeardownCoordinator.State.IDLE, StreamTeardownCoordinator.currentState())
        assertFalse(StreamTeardownCoordinator.isInProgress())
    }

    @Test
    fun stopPublisherPreservingWarmScreenFgs_endsIdleWithoutRequestingStopAll() {
        // Contract: handoff must leave coordinator IDLE and must not arm a JS STOP watchdog.
        StreamTeardownCoordinator.stopPublisherPreservingWarmScreenFgs(context, "unit-preserve")
        assertEquals(StreamTeardownCoordinator.State.IDLE, StreamTeardownCoordinator.currentState())
        assertFalse(StreamTeardownCoordinator.isInProgress())
    }
}
