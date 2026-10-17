package com.childmonitor.capture

import android.view.Surface

/**
 * Single source of truth for remote-camera orientation on the child device.
 *
 * Stream policy: portrait-upright while held normally; CVO changes on horizontal tilt.
 * CameraX stays at ROTATION_0 — orientation is sent via WebRTC CVO (MIUI-safe).
 */
object CameraOrientationHelper {

    /**
     * Map display rotation to the surface bucket used for CVO.
     * Portrait and upside-down display → portrait stream (ROTATION_0).
     * Landscape display → follow horizontal orientation.
     */
    fun streamSurfaceRotation(displaySurfaceRotation: Int): Int =
        when (displaySurfaceRotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> displaySurfaceRotation
            else -> Surface.ROTATION_0
        }

    /** Clockwise degrees from [Surface.ROTATION_*]. */
    fun deviceRotationDegrees(surfaceRotation: Int): Int =
        when (surfaceRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

    /**
     * Map [OrientationEventListener] degrees to [Surface.ROTATION_*].
     * 0° = natural portrait; 90/270 = landscape tilts; 180 = upside-down.
     */
    fun surfaceRotationFromOrientationDegrees(orientationDegrees: Int): Int {
        val normalized = ((orientationDegrees % 360) + 360) % 360
        return when {
            normalized >= 315 || normalized < 45 -> Surface.ROTATION_0
            normalized < 135 -> Surface.ROTATION_90
            normalized < 225 -> Surface.ROTATION_180
            else -> Surface.ROTATION_270
        }
    }

    /** Typical sensor mount when CameraX reports 0 or null (MIUI right after bind). */
    fun defaultSensorRotationDegrees(frontCamera: Boolean): Int =
        if (frontCamera) 270 else 90

    fun resolveSensorRotationDegrees(frontCamera: Boolean, sensorRotationDegrees: Int?): Int {
        if (sensorRotationDegrees != null && sensorRotationDegrees != 0) {
            return sensorRotationDegrees
        }
        return defaultSensorRotationDegrees(frontCamera)
    }

    /**
     * Clockwise CVO for a WebRTC VideoFrame.
     * Front: (sensor + (360 − device)) mod 360
     * Back:  (sensor + device) mod 360
     */
    fun webrtcFrameRotationDegrees(
        surfaceRotation: Int,
        frontCamera: Boolean = false,
        sensorRotationDegrees: Int? = null,
    ): Int {
        val device = deviceRotationDegrees(surfaceRotation)
        val sensor = resolveSensorRotationDegrees(frontCamera, sensorRotationDegrees)
        return if (frontCamera) {
            (sensor + (360 - device)) % 360
        } else {
            (sensor + device) % 360
        }
    }
}
