package com.childmonitor.capture

import android.view.Surface

/**
 * Maps device orientation (gravity) to WebRTC frame rotation so parent / web
 * viewers see an upright camera frame when the child phone is tilted.
 *
 * CameraX keeps a stable target rotation; rotation is published on the WebRTC
 * frame (CVO) instead of re-scaling the capture buffer (which skews aspect).
 */
object CameraOrientationHelper {

    /**
     * Convert [android.view.OrientationEventListener] degrees (0–359, -1 unknown)
     * to a [Surface] rotation constant, or null if orientation is unknown.
     *
     * Buckets match [androidx.camera.core.UseCase.snapToSurfaceRotation].
     */
    fun surfaceRotationOrNull(orientationDegrees: Int): Int? {
        if (orientationDegrees < 0) return null
        val normalized = ((orientationDegrees % 360) + 360) % 360
        return when {
            normalized >= 315 || normalized < 45 -> Surface.ROTATION_0
            normalized >= 225 -> Surface.ROTATION_270
            normalized >= 135 -> Surface.ROTATION_180
            else -> Surface.ROTATION_90
        }
    }

    /** Surface.* rotation → clockwise degrees expected by [org.webrtc.SurfaceTextureHelper]. */
    fun webrtcFrameRotationDegrees(surfaceRotation: Int): Int =
        when (surfaceRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
}
