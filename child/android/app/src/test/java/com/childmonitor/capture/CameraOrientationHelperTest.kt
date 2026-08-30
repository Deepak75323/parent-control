package com.childmonitor.capture

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOrientationHelperTest {

    @Test
    fun streamSurfaceRotation_locksPortraitForNormalAndUpsideDownDisplay() {
        assertEquals(Surface.ROTATION_0, CameraOrientationHelper.streamSurfaceRotation(Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_0, CameraOrientationHelper.streamSurfaceRotation(Surface.ROTATION_180))
    }

    @Test
    fun streamSurfaceRotation_followsLandscapeDisplay() {
        assertEquals(Surface.ROTATION_90, CameraOrientationHelper.streamSurfaceRotation(Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_270, CameraOrientationHelper.streamSurfaceRotation(Surface.ROTATION_270))
    }

    @Test
    fun surfaceRotationFromOrientationDegrees_mapsLandscapeTilts() {
        assertEquals(Surface.ROTATION_0, CameraOrientationHelper.surfaceRotationFromOrientationDegrees(0))
        assertEquals(Surface.ROTATION_0, CameraOrientationHelper.surfaceRotationFromOrientationDegrees(359))
        assertEquals(Surface.ROTATION_90, CameraOrientationHelper.surfaceRotationFromOrientationDegrees(90))
        assertEquals(Surface.ROTATION_180, CameraOrientationHelper.surfaceRotationFromOrientationDegrees(180))
        assertEquals(Surface.ROTATION_270, CameraOrientationHelper.surfaceRotationFromOrientationDegrees(270))
    }

    @Test
    fun webrtcFrameRotationDegrees_frontPortrait_matchesCamera2Session() {
        assertEquals(
            270,
            CameraOrientationHelper.webrtcFrameRotationDegrees(
                Surface.ROTATION_0,
                frontCamera = true,
                sensorRotationDegrees = 270,
            ),
        )
    }

    @Test
    fun webrtcFrameRotationDegrees_frontLandscapeLeft_matchesCamera2Session() {
        assertEquals(
            180,
            CameraOrientationHelper.webrtcFrameRotationDegrees(
                Surface.ROTATION_90,
                frontCamera = true,
                sensorRotationDegrees = 270,
            ),
        )
    }

    @Test
    fun webrtcFrameRotationDegrees_frontLandscapeRight_matchesCamera2Session() {
        assertEquals(
            0,
            CameraOrientationHelper.webrtcFrameRotationDegrees(
                Surface.ROTATION_270,
                frontCamera = true,
                sensorRotationDegrees = 270,
            ),
        )
    }

    @Test
    fun webrtcFrameRotationDegrees_backPortrait_matchesCamera2Session() {
        assertEquals(
            90,
            CameraOrientationHelper.webrtcFrameRotationDegrees(
                Surface.ROTATION_0,
                frontCamera = false,
                sensorRotationDegrees = 90,
            ),
        )
    }

    @Test
    fun webrtcFrameRotationDegrees_usesSensorFallbackWhenUnavailable() {
        assertEquals(
            270,
            CameraOrientationHelper.webrtcFrameRotationDegrees(
                Surface.ROTATION_0,
                frontCamera = true,
                sensorRotationDegrees = null,
            ),
        )
    }
}
