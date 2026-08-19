package com.childmonitor.capture

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraOrientationHelperTest {

    @Test
    fun surfaceRotationOrNull_rejectsUnknown() {
        assertNull(CameraOrientationHelper.surfaceRotationOrNull(-1))
    }

    @Test
    fun surfaceRotationOrNull_snapsPortraitAndLandscapeBuckets() {
        // Portrait upright (~0°)
        assertEquals(Surface.ROTATION_0, CameraOrientationHelper.surfaceRotationOrNull(0))
        assertEquals(Surface.ROTATION_0, CameraOrientationHelper.surfaceRotationOrNull(10))
        // Landscape left (~90°)
        assertEquals(Surface.ROTATION_90, CameraOrientationHelper.surfaceRotationOrNull(90))
        // Upside down (~180°)
        assertEquals(Surface.ROTATION_180, CameraOrientationHelper.surfaceRotationOrNull(180))
        // Landscape right (~270°)
        assertEquals(Surface.ROTATION_270, CameraOrientationHelper.surfaceRotationOrNull(270))
    }

    @Test
    fun webrtcFrameRotationDegrees_mapsSurfaceConstants() {
        assertEquals(0, CameraOrientationHelper.webrtcFrameRotationDegrees(Surface.ROTATION_0))
        assertEquals(90, CameraOrientationHelper.webrtcFrameRotationDegrees(Surface.ROTATION_90))
        assertEquals(180, CameraOrientationHelper.webrtcFrameRotationDegrees(Surface.ROTATION_180))
        assertEquals(270, CameraOrientationHelper.webrtcFrameRotationDegrees(Surface.ROTATION_270))
    }
}
