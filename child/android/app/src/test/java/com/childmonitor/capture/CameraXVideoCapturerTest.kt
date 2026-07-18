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
}
