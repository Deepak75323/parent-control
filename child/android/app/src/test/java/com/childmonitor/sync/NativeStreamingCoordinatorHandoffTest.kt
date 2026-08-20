package com.childmonitor.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStreamingCoordinatorHandoffTest {

    @Test
    fun sameSessionIdsWithoutLivePipelineAreNotDuplicate() {
        assertFalse(
            NativeStreamingHandoffPolicy.isSameSessionAlreadyLive(
                activeSessionId = "sess-1",
                activeRoomId = "room-1",
                requestedSessionId = "sess-1",
                requestedRoomId = "room-1",
                nativePublisherRunning = false,
                registrySessionId = null,
                screenFgsActive = false,
                cameraActive = false,
                audioActive = false,
                startInProgress = false,
            ),
        )
    }

    @Test
    fun sameSessionIdsWithNativePublisherRunningAreDuplicate() {
        assertTrue(
            NativeStreamingHandoffPolicy.isSameSessionAlreadyLive(
                activeSessionId = "sess-1",
                activeRoomId = "room-1",
                requestedSessionId = "sess-1",
                requestedRoomId = "room-1",
                nativePublisherRunning = true,
                registrySessionId = null,
                screenFgsActive = false,
                cameraActive = false,
                audioActive = false,
                startInProgress = false,
            ),
        )
    }

    @Test
    fun sameSessionIdsWithStartInProgressAreDuplicate() {
        assertTrue(
            NativeStreamingHandoffPolicy.isSameSessionAlreadyLive(
                activeSessionId = "sess-1",
                activeRoomId = "room-1",
                requestedSessionId = "sess-1",
                requestedRoomId = "room-1",
                nativePublisherRunning = false,
                registrySessionId = null,
                screenFgsActive = false,
                cameraActive = false,
                audioActive = false,
                startInProgress = true,
            ),
        )
    }

    @Test
    fun mismatchedSessionIsNeverDuplicate() {
        assertFalse(
            NativeStreamingHandoffPolicy.isSameSessionAlreadyLive(
                activeSessionId = "sess-old",
                activeRoomId = "room-1",
                requestedSessionId = "sess-new",
                requestedRoomId = "room-1",
                nativePublisherRunning = true,
                registrySessionId = "sess-old",
                screenFgsActive = true,
                cameraActive = true,
                audioActive = true,
                startInProgress = true,
            ),
        )
    }
}
