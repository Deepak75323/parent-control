package com.childmonitor.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FacingMode semantics used by Flip / applyConstraints.
 * RN MediaStreamTrack._switchCamera toggles from getSettings().facingMode —
 * missing facingMode always resolved to "user" and Flip no-op'd on front cam.
 */
class CameraXFacingModeTest {

    @Test
    fun wantsFrontCamera_nullOrUser_isFront() {
        assertTrue(CameraXVideoCapturer.wantsFrontCamera(null))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera("user"))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera("front"))
    }

    @Test
    fun wantsFrontCamera_environment_isBack() {
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("environment"))
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("camera2 environment"))
    }

    @Test
    fun missingFacingMode_legacyToggleAlwaysPickedUser() {
        // Documents the RN _switchCamera bug: undefined === "user" is false → "user".
        val settingsFacingMode: String? = null
        val next = if (settingsFacingMode == "user") "environment" else "user"
        assertEquals("user", next)
    }

    @Test
    fun absoluteFacing_requestBackThenFront() {
        // Controller must request absolute facingMode, not a one-way toggle.
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("environment"))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera("user"))
    }
}
