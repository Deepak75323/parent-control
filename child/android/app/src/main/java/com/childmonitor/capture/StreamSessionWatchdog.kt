package com.childmonitor.capture

import android.content.Context
import android.util.Log
import com.childmonitor.telemetry.StreamLifecycleTelemetry
import com.oney.WebRTCModule.HiddenAudioBridge
import com.oney.WebRTCModule.HiddenCameraBridge
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P1-7: Periodic orphan session cleanup when registry diverges from runtime.
 * Also releases camera/mic when the parent viewer ICE path is confirmed idle.
 *
 * Teardown is single-flight so overlapping poll ticks cannot spawn duplicate
 * capturer stops or leave the camera half-released.
 */
object StreamSessionWatchdog {

    private const val TAG = "StreamSessionWatchdog"
    private const val ORPHAN_AGE_MS = 120_000L
    private val teardownInFlight = AtomicBoolean(false)

    fun tick(context: Context) {
        val snap = StreamSessionRegistry.snapshot()
        val ageMs = (snap["ageMs"] as? Long) ?: 0L
        val nativeRunning = NativeStreamPublisher.isRunning()
        val owner = StreamSessionRegistry.activeOwner()

        if (owner == StreamSessionRegistry.Owner.NATIVE_PUBLISHER && !nativeRunning) {
            StreamLifecycleTelemetry.record("watchdog_orphan_native", snap)
            StreamSessionRegistry.release(owner)
            try {
                NativeStreamPublisher.stop(context)
            } catch (e: Exception) {
                Log.w(TAG, "orphan native stop failed", e)
            }
            return
        }

        if (localJsCaptureActive() && JsViewerPresenceTracker.shouldReleaseCapture()) {
            if (!teardownInFlight.compareAndSet(false, true)) {
                return
            }
            try {
                StreamLifecycleTelemetry.record(
                    "watchdog_viewer_idle",
                    JsViewerPresenceTracker.snapshot(),
                )
                JsViewerPresenceTracker.reset()
                StreamCaptureTeardown.stopAll(context, "parent viewer idle (watchdog)")
            } catch (e: Exception) {
                Log.w(TAG, "viewer-idle teardown failed", e)
            } finally {
                teardownInFlight.set(false)
            }
            return
        }

        if (owner != StreamSessionRegistry.Owner.NONE &&
            ageMs > ORPHAN_AGE_MS &&
            snap["phase"] == StreamSessionRegistry.LifecyclePhase.ACQUIRING.name
        ) {
            StreamLifecycleTelemetry.record("watchdog_stuck_acquiring", snap)
            StreamSessionRegistry.release(owner)
        }
    }

    private fun localJsCaptureActive(): Boolean =
        HiddenCameraBridge.isCaptureActive() ||
            HiddenAudioBridge.isCaptureActive() ||
            CameraStreamForegroundService.isActive() ||
            AudioStreamForegroundService.isActive()

    /** Test hook. */
    fun resetForTests() {
        teardownInFlight.set(false)
    }
}
