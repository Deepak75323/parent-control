package com.childmonitor.capture

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JsViewerPresenceTrackerTest {

    private var fakeNow = 1_000_000L

    @Before
    fun setUp() {
        fakeNow = 1_000_000L
        JsViewerPresenceTracker.nowMs = { fakeNow }
        JsViewerPresenceTracker.reset()
    }

    @After
    fun tearDown() {
        JsViewerPresenceTracker.reset()
        JsViewerPresenceTracker.nowMs = { System.currentTimeMillis() }
    }

    @Test
    fun connectedStateDoesNotRequestRelease() {
        JsViewerPresenceTracker.onIceState("connected")
        fakeNow += 60_000L
        assertFalse(JsViewerPresenceTracker.shouldReleaseCapture())
    }

    @Test
    fun failedStateRequestsReleaseAfterGrace() {
        JsViewerPresenceTracker.onIceState("failed")
        fakeNow += 7_999L
        assertFalse(JsViewerPresenceTracker.shouldReleaseCapture())
        fakeNow += 2L
        assertTrue(JsViewerPresenceTracker.shouldReleaseCapture())
    }

    @Test
    fun disconnectedStateWaitsLongerGrace() {
        JsViewerPresenceTracker.onIceState("disconnected")
        fakeNow += 19_999L
        assertFalse(JsViewerPresenceTracker.shouldReleaseCapture())
        fakeNow += 2L
        assertTrue(JsViewerPresenceTracker.shouldReleaseCapture())
    }

    @Test
    fun peerConnectionConnectionStateValuesAreIgnored() {
        // PeerConnection.connectionState values that are NOT iceConnectionState.
        JsViewerPresenceTracker.onIceState("connecting")
        JsViewerPresenceTracker.onIceState("unknown")
        assertFalse(JsViewerPresenceTracker.shouldReleaseCapture())
        fakeNow += 60_000L
        assertFalse(JsViewerPresenceTracker.shouldReleaseCapture())
    }

    @Test
    fun identicalFailedReportsDoNotResetGraceClock() {
        JsViewerPresenceTracker.onIceState("failed")
        fakeNow += 4_000L
        JsViewerPresenceTracker.onIceState("failed")
        fakeNow += 4_001L
        assertTrue(JsViewerPresenceTracker.shouldReleaseCapture())
    }

    @Test
    fun recoverToConnectedClearsRelease() {
        JsViewerPresenceTracker.onIceState("failed")
        fakeNow += 10_000L
        assertTrue(JsViewerPresenceTracker.shouldReleaseCapture())
        JsViewerPresenceTracker.onIceState("connected")
        assertFalse(JsViewerPresenceTracker.shouldReleaseCapture())
    }
}
