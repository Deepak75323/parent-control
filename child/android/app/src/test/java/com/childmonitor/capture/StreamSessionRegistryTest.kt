package com.childmonitor.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    fun reconcile_defersOrphanWhileAcquiring() {
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.JS_HIDDEN,
            "session-acquiring",
            "room-1",
        )
        assertTrue(StreamSessionRegistry.reconcile(nativePublisherRunning = false, jsPeerActive = false))
        assertEquals(StreamSessionRegistry.Owner.JS_HIDDEN, StreamSessionRegistry.activeOwner())
    }

    @Test
    fun reconcile_releasesActiveJsOrphan() {
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.JS_HIDDEN,
            "session-orphan",
            "room-1",
        )
        StreamSessionRegistry.markActive()
        assertFalse(StreamSessionRegistry.reconcile(nativePublisherRunning = false, jsPeerActive = false))
        assertEquals(StreamSessionRegistry.Owner.NONE, StreamSessionRegistry.activeOwner())
    }

    @Test
    fun reconcile_orphanTriggersCaptureTeardown() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val before = StreamCaptureTeardown.invocationCount()
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.JS_HIDDEN,
            "session-teardown",
            "room-1",
        )
        StreamSessionRegistry.markActive()
        if (!StreamSessionRegistry.reconcile(nativePublisherRunning = false, jsPeerActive = false)) {
            StreamCaptureTeardown.stopAll(context, "session_orphan")
        }
        assertEquals(before + 1, StreamCaptureTeardown.invocationCount())
        assertEquals("session_orphan", StreamCaptureTeardown.lastReason)
    }
}
