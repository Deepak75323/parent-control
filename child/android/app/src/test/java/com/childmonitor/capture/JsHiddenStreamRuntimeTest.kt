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
class JsHiddenStreamRuntimeTest {

    @After
    fun tearDown() {
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.JS_HIDDEN)
    }

    @Test
    fun isCapturePipelineLive_falseWhenNoOwner() {
        assertFalse(JsHiddenStreamRuntime.isCapturePipelineLive())
    }

    @Test
    fun isCapturePipelineLive_falseWhenJsOwnerWithoutCapture() {
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.JS_HIDDEN,
            "session-no-capture",
            "room-1",
        )
        StreamSessionRegistry.markActive()
        assertFalse(JsHiddenStreamRuntime.isCapturePipelineLive())
    }

    @Test
    fun isCapturePipelineLive_falseForNativePublisher() {
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.NATIVE_PUBLISHER,
            "session-native",
            "room-1",
        )
        assertFalse(JsHiddenStreamRuntime.isCapturePipelineLive())
    }

    @Test
    fun reconcile_orphansStaleJsOwnerWhenOnlyReactWouldBeAlive() {
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.JS_HIDDEN,
            "session-stale",
            "room-1",
        )
        StreamSessionRegistry.markActive()
        assertFalse(
            StreamSessionRegistry.reconcile(
                nativePublisherRunning = false,
                jsPeerActive = JsHiddenStreamRuntime.isCapturePipelineLive(),
            ),
        )
        assertTrue(StreamSessionRegistry.activeOwner() == StreamSessionRegistry.Owner.NONE)
    }
}
