package com.childmonitor.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraXVideoCapturerTest {

    @Test
    fun wantsFrontCamera_defaultsToFrontWhenNameMissing() {
        assertTrue(CameraXVideoCapturer.wantsFrontCamera(null))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera(""))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera("user"))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera("front"))
    }

    @Test
    fun wantsFrontCamera_detectsEnvironmentBackLens() {
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("environment"))
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("ENVIRONMENT"))
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("camera2 environment"))
    }

    @Test
    fun widestFovCaptureDimensions_prefers4x3Over16x9Request() {
        val (width, height) = CameraXVideoCapturer.widestFovCaptureDimensions(1280, 720)
        assertTrue(width == 1280)
        assertTrue(height == 960)
    }

    @Test
    fun widestFovCaptureDimensions_capsAt1920() {
        val (width, height) = CameraXVideoCapturer.widestFovCaptureDimensions(3840, 2160)
        assertTrue(width == 1920)
        assertTrue(height == 1440)
    }
}
