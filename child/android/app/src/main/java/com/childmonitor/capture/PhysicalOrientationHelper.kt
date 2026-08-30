package com.childmonitor.capture

import android.view.Surface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Maps accelerometer gravity to device tilt, with Schmitt-trigger hysteresis.
 *
 * Matches the idea behind [android.view.OrientationEventListener] and Camera2 display
 * orientation: do not flip on small wobbles — enter landscape only after a clear horizontal
 * tilt (~60°), return to portrait only when nearly upright again (~35°).
 *
 * @see <a href="https://developer.android.com/reference/android/view/OrientationEventListener">OrientationEventListener</a>
 */
object PhysicalOrientationHelper {

    private const val GRAVITY_MIN = 3.0f

    /** Must tilt past this (degrees) before leaving portrait. */
    const val ENTER_LANDSCAPE_DEG = 60

    /** Must return within this (degrees) of upright before leaving landscape. */
    const val EXIT_TO_PORTRAIT_DEG = 35

    /** Near-level horizontal — commit faster (Camera2 snaps at ~90/270). */
    const val CLEARLY_HORIZONTAL_MIN = 78
    const val CLEARLY_HORIZONTAL_MAX = 102
    private const val CLEARLY_HORIZONTAL_ALT_MIN = 258
    private const val CLEARLY_HORIZONTAL_ALT_MAX = 282

    fun isClearlyHorizontal(angle: Int): Boolean =
        angle in CLEARLY_HORIZONTAL_MIN..CLEARLY_HORIZONTAL_MAX ||
            angle in CLEARLY_HORIZONTAL_ALT_MIN..CLEARLY_HORIZONTAL_ALT_MAX

    /** Near-level portrait — snap back quickly from a stale landscape commit. */
    const val CLEARLY_PORTRAIT_DEG = 15

    fun isClearlyPortrait(angle: Int): Boolean =
        angle <= CLEARLY_PORTRAIT_DEG || angle >= 360 - CLEARLY_PORTRAIT_DEG

    /**
     * Clockwise degrees from portrait-upright (0°).
     * atan2(-x, -y): 0 = top up, 90 = top left, 270 = top right.
     */
    fun tiltDegreesFromGravity(x: Float, y: Float): Int {
        if (abs(x) < GRAVITY_MIN && abs(y) < GRAVITY_MIN) {
            return 0
        }
        val degrees = Math.toDegrees(atan2(-x.toDouble(), -y.toDouble())).roundToInt()
        return ((degrees % 360) + 360) % 360
    }

    /**
     * Surface bucket with hysteresis — [committed] is the last stable orientation.
     * Prevents rapid toggling in the 35°–60° dead band.
     */
    fun surfaceRotationFromGravity(
        x: Float,
        y: Float,
        committed: Int = Surface.ROTATION_0,
    ): Int = surfaceRotationWithHysteresis(tiltDegreesFromGravity(x, y), committed)

    internal fun surfaceRotationWithHysteresis(angle: Int, committed: Int): Int {
        if (isClearlyPortrait(angle)) {
            return Surface.ROTATION_0
        }

        val nearPortrait = angle < EXIT_TO_PORTRAIT_DEG || angle >= 360 - EXIT_TO_PORTRAIT_DEG
        // Landscape only when nearly level horizontal — not on a partial ~60° tilt.
        val landscapeLeft = angle in CLEARLY_HORIZONTAL_MIN..CLEARLY_HORIZONTAL_MAX
        val landscapeRight = angle in CLEARLY_HORIZONTAL_ALT_MIN..CLEARLY_HORIZONTAL_ALT_MAX

        return when (committed) {
            Surface.ROTATION_90 ->
                when {
                    nearPortrait -> Surface.ROTATION_0
                    landscapeRight -> Surface.ROTATION_270
                    else -> Surface.ROTATION_90
                }
            Surface.ROTATION_270 ->
                when {
                    nearPortrait -> Surface.ROTATION_0
                    landscapeLeft -> Surface.ROTATION_90
                    else -> Surface.ROTATION_270
                }
            else ->
                when {
                    landscapeLeft -> Surface.ROTATION_90
                    landscapeRight -> Surface.ROTATION_270
                    else -> Surface.ROTATION_0
                }
        }
    }

    /** Portrait-locked stream policy — only landscape tilts change CVO. */
    fun streamSurfaceFromGravity(
        x: Float,
        y: Float,
        committedSurface: Int = Surface.ROTATION_0,
    ): Int =
        CameraOrientationHelper.streamSurfaceRotation(
            surfaceRotationFromGravity(x, y, committedSurface),
        )

    /**
     * [OrientationEventListener] degrees (0 = portrait) with hysteresis.
     * Upside-down (~180°) stays on portrait stream.
     */
    fun surfaceRotationFromOrientation(
        orientationDegrees: Int,
        committed: Int = Surface.ROTATION_0,
    ): Int {
        val angle = ((orientationDegrees % 360) + 360) % 360
        if (angle in UPSIDE_DOWN_MIN..UPSIDE_DOWN_MAX) {
            return Surface.ROTATION_0
        }
        return surfaceRotationWithHysteresis(angle, committed)
    }

    fun streamSurfaceFromOrientation(
        orientationDegrees: Int,
        committedSurface: Int = Surface.ROTATION_0,
    ): Int =
        CameraOrientationHelper.streamSurfaceRotation(
            surfaceRotationFromOrientation(orientationDegrees, committedSurface),
        )

    /** Upside-down — keep portrait stream (matches streamSurfaceRotation policy). */
    private const val UPSIDE_DOWN_MIN = 165
    private const val UPSIDE_DOWN_MAX = 195
}
