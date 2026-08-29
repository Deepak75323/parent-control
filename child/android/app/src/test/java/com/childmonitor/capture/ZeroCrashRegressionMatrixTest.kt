package com.childmonitor.capture

import android.content.pm.ServiceInfo
import com.childmonitor.sync.NativeStreamingHandoffPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stress/regression matrix for zero-crash hardening (Phases 1–3).
 *
 * Simulates rapid lifecycle churn in pure logic — no device required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZeroCrashRegressionMatrixTest {

    @Test
    fun cameraFlipGeneration_survives100RapidStopFlipCycles() {
        var generation = 0L
        repeat(100) { cycle ->
            generation++ // flip scheduled
            val scheduled = generation
            generation++ // stopCapture invalidates pending rebind
            assertFalse(
                "cycle=$cycle stale rebind must not run after stop",
                CameraXVideoCapturer.isRebindGenerationCurrent(scheduled, generation),
            )
            generation++ // next flip
            val active = generation
            assertTrue(
                "cycle=$cycle current flip callback must remain valid",
                CameraXVideoCapturer.isRebindGenerationCurrent(active, generation),
            )
        }
    }

    @Test
    fun publisherGeneration_survives100StopStartSessionReplacements() {
        repeat(100) { cycle ->
            val startGen = (cycle + 1).toLong()
            val stopGen = startGen + 1
            val session = "sess-$cycle"
            assertTrue(
                NativeStreamPublisher.isActiveGenerationForTesting(
                    generation = startGen,
                    expectedSessionId = session,
                    runningValue = true,
                    activeSessionIdValue = session,
                    currentGeneration = startGen,
                ),
            )
            assertFalse(
                "stale offer after stop gen bump cycle=$cycle",
                NativeStreamPublisher.isActiveGenerationForTesting(
                    generation = startGen,
                    expectedSessionId = session,
                    runningValue = true,
                    activeSessionIdValue = session,
                    currentGeneration = stopGen,
                ),
            )
        }
    }

    @Test
    fun jsWakeFailure_doesNotBlockNativeFallbackAcross100PollCycles() {
        repeat(100) { cycle ->
            val session = "sess-$cycle"
            val room = "room-$cycle"
            assertFalse(
                "failed JS wake must not look live cycle=$cycle",
                NativeStreamingHandoffPolicy.isSameSessionAlreadyLive(
                    activeSessionId = session,
                    activeRoomId = room,
                    requestedSessionId = session,
                    requestedRoomId = room,
                    nativePublisherRunning = false,
                    registrySessionId = null,
                    screenFgsActive = false,
                    cameraActive = false,
                    audioActive = false,
                    startInProgress = false,
                ),
            )
            assertTrue(
                "in-flight start must dedupe cycle=$cycle",
                NativeStreamingHandoffPolicy.isSameSessionAlreadyLive(
                    activeSessionId = session,
                    activeRoomId = room,
                    requestedSessionId = session,
                    requestedRoomId = room,
                    nativePublisherRunning = false,
                    registrySessionId = null,
                    screenFgsActive = false,
                    cameraActive = false,
                    audioActive = false,
                    startInProgress = true,
                ),
            )
        }
    }

    @Test
    fun cameraPermissionRevoked_neverFallsBackToMicrophoneType() {
        repeat(100) {
            assertFalse(
                ForegroundServiceCrashGuard.cameraPromotionTypes(hasCameraPermission = false)
                    .contains(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE),
            )
        }
    }
}
