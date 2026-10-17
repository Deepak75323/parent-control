package com.parentscaffold.capture

/**
 * Renderer rotation for remote camera frames on [org.webrtc.SurfaceViewRenderer].
 *
 * Web canvas uses clockwise degrees; [org.webrtc.SurfaceViewRenderer.setRotation] needs the
 * mirror of that correction, except portrait CVO (270) which is already upright at 0°.
 */
object CameraFrameRotation {

    fun rendererRotationDegrees(
        cvo: Int,
        manualRotateDeg: Int = 0,
        frontCamera: Boolean = true,
    ): Int {
        if (!frontCamera) {
            return normalizeDegrees(cvo + manualRotateDeg)
        }
        val cvoNorm = normalizeDegrees(cvo)
        val base =
            when (cvoNorm) {
                270 -> 0
                else -> normalizeDegrees(360 - webCanvasRotationDegrees(cvoNorm))
            }
        return normalizeDegrees(base + manualRotateDeg)
    }

    /** Same front-camera correction as [web displayRotationDegrees] (landscape-coded buffer). */
    internal fun webCanvasRotationDegrees(cvoNorm: Int): Int {
        val mirrored = normalizeDegrees(360 - cvoNorm)
        return when (normalizeDegrees(cvoNorm)) {
            0, 180 -> normalizeDegrees(mirrored + 90)
            else -> mirrored
        }
    }

    private fun normalizeDegrees(degrees: Int): Int = ((degrees % 360) + 360) % 360
}
