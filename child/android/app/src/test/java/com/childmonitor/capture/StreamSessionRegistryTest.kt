package com.childmonitor.capture

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StreamSessionRegistryTest {

    @After
    fun tearDown() {
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.JS_HIDDEN)
    }

    @Test
    fun isNegotiatingOrReconnecting_falseWhenIdle() {
        assertFalse(StreamSessionRegistry.isNegotiatingOrReconnecting())
    }

    @Test
    fun isNegotiatingOrReconnecting_trueDuringNegotiating() {
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.NATIVE_PUBLISHER,
            "session-1",
            "room-1",
        )
        StreamSessionRegistry.markPhase(StreamSessionRegistry.LifecyclePhase.NEGOTIATING)
        assertTrue(StreamSessionRegistry.isNegotiatingOrReconnecting())
    }

    @Test
    fun isNegotiatingOrReconnecting_falseWhenActive() {
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.NATIVE_PUBLISHER,
            "session-2",
            "room-2",
        )
        StreamSessionRegistry.markActive()
        assertFalse(StreamSessionRegistry.isNegotiatingOrReconnecting())
    }
}
