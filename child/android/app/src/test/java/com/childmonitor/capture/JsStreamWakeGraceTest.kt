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
class JsStreamWakeGraceTest {

    @After
    fun tearDown() {
        JsStreamWakeGrace.clear()
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.JS_HIDDEN)
    }

    @Test
    fun suppressesActivePollMissDuringPendingWake() {
        JsStreamWakeGrace.markWakeEmitted("room-1", "CAMERA", "sess-1", null, nowMs = 1_000L)
        assertTrue(
            JsStreamWakeGrace.shouldSuppressActivePollMiss(
                activeSessionId = "sess-1",
                cameraFgsActive = true,
                audioFgsActive = false,
                nowMs = 5_000L,
            ),
        )
    }

    @Test
    fun stopsSuppressingAfterGraceExpires() {
        JsStreamWakeGrace.markWakeEmitted("room-1", "CAMERA", "sess-1", null, nowMs = 0L)
        assertFalse(
            JsStreamWakeGrace.shouldSuppressActivePollMiss(
                activeSessionId = "sess-1",
                cameraFgsActive = true,
                audioFgsActive = false,
                nowMs = JsStreamWakeGrace.GRACE_MS + 1L,
            ),
        )
    }

    @Test
    fun suppressesWhileCameraFgsUpBeforeRegistryAcquire() {
        assertTrue(
            JsStreamWakeGrace.shouldSuppressActivePollMiss(
                activeSessionId = "sess-2",
                cameraFgsActive = true,
                audioFgsActive = false,
            ),
        )
    }

    @Test
    fun clearsWhenJsCaptureIsLive() {
        JsStreamWakeGrace.markWakeEmitted("room-1", "CAMERA", "sess-3", null)
        StreamSessionRegistry.tryAcquire(
            StreamSessionRegistry.Owner.JS_HIDDEN,
            "sess-3",
            "room-1",
        )
        assertTrue(JsStreamWakeGrace.isJsCaptureLive("sess-3"))
        assertFalse(
            JsStreamWakeGrace.shouldSuppressActivePollMiss(
                activeSessionId = "sess-3",
                cameraFgsActive = true,
                audioFgsActive = false,
            ),
        )
    }
}
