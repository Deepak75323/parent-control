package com.childmonitor.capture

import android.content.Context
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface

/**
 * Tracks physical phone tilt via [OrientationEventListener] (device-native angles).
 *
 * Raw accelerometer axes are wrong on some OEMs (e.g. Xiaomi reports ~84° when upright);
 * the orientation listener uses the same angles as the system / Camera2 stack.
 */
class DeviceOrientationTracker(
    context: Context,
    private val onStreamSurfaceRotationChanged: (Int) -> Unit,
) : OrientationEventListener(context.applicationContext, SensorManager.SENSOR_DELAY_NORMAL) {

    private var committedSurface = Surface.ROTATION_0
    private var pendingSurface = Surface.ROTATION_0
    private var pendingCount = 0
    private var pendingSinceMs = 0L

    fun start(): Boolean {
        if (!canDetectOrientation()) {
            return false
        }
        committedSurface = Surface.ROTATION_0
        pendingSurface = Surface.ROTATION_0
        pendingCount = 0
        pendingSinceMs = 0L
        enable()
        return true
    }

    fun stop() {
        disable()
    }

    override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) {
            return
        }
        val streamSurface =
            PhysicalOrientationHelper.streamSurfaceFromOrientation(orientation, committedSurface)
        val requiredHoldMs =
            when {
                PhysicalOrientationHelper.isClearlyPortrait(orientation) ->
                    CLEAR_PORTRAIT_HOLD_MS
                PhysicalOrientationHelper.isClearlyHorizontal(orientation) ->
                    CLEAR_HORIZONTAL_HOLD_MS
                else -> MIN_HOLD_MS
            }
        if (streamSurface == committedSurface) {
            pendingSurface = streamSurface
            pendingCount = 0
            pendingSinceMs = 0L
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (streamSurface == pendingSurface) {
            pendingCount++
        } else {
            pendingSurface = streamSurface
            pendingCount = 1
            pendingSinceMs = now
        }
        val heldMs = now - pendingSinceMs
        if (pendingCount >= STABLE_SAMPLES && heldMs >= requiredHoldMs) {
            committedSurface = streamSurface
            pendingCount = 0
            pendingSinceMs = 0L
            Log.i(TAG, "Committed streamSurface=$streamSurface orientation=${orientation}°")
            onStreamSurfaceRotationChanged(streamSurface)
        }
    }

    companion object {
        /** Consecutive readings in the new sector. */
        private const val STABLE_SAMPLES = 6

        /** Minimum time the phone must hold a partial tilt (ms). */
        private const val MIN_HOLD_MS = 750L

        /** Shorter hold when the phone is level horizontal (~90°/270°). */
        private const val CLEAR_HORIZONTAL_HOLD_MS = 350L

        /** Shorter hold when the phone is clearly upright again. */
        private const val CLEAR_PORTRAIT_HOLD_MS = 250L

        private const val TAG = "DeviceOrientationTracker"
    }
}
