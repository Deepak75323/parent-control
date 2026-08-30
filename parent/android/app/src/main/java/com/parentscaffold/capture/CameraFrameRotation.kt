package com.parentscaffold.capture

/**
 * Renderer rotation for remote camera frames.
 *
 * The child pins CameraX to `Surface.ROTATION_0`, so the buffer always holds portrait-upright
 * pixels regardless of how the phone is held, and signals the physical tilt purely through the
 * frame's CVO. [org.webrtc.SurfaceViewRenderer] applying that CVO as-is over-rotates by the
 * portrait baseline, which is what tipped the image on its side.
 *
 * Re-basing the CVO against portrait keeps the child upright when held normally and still
 * follows a horizontal tilt, matching the web portal.
 */
object CameraFrameRotation {

    /** CVO the child sends while held upright in portrait (front sensor mounted at 270). */
    private const val PORTRAIT_CVO = 270

    /**
     * Rotation the renderer should apply.
     *
     * The CVO counts opposite the viewer's rotation, so it is subtracted rather than added.
     * Verified on device: cvo=270 (portrait) is upright at 0, cvo=180 (tilted) at 90.
     */
    fun rendererRotationDegrees(cvo: Int, manualRotateDeg: Int = 0): Int =
        normalizeDegrees(PORTRAIT_CVO - cvo + manualRotateDeg)

    private fun normalizeDegrees(degrees: Int): Int = ((degrees % 360) + 360) % 360
}
