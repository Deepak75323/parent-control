package com.childmonitor.fcm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmStreamControlActionsTest {

    @Test
    fun recognizesSwitchCameraAction() {
        assertTrue(FcmStreamControlActions.isSwitchCamera("SWITCH_CAMERA", null))
        assertTrue(FcmStreamControlActions.isSwitchCamera("switch_camera", ""))
        assertTrue(FcmStreamControlActions.isSwitchCamera("STREAM_CONTROL", "SWITCH_CAMERA"))
    }

    @Test
    fun ignoresUnrelatedActions() {
        assertFalse(FcmStreamControlActions.isSwitchCamera("STREAM_REQUEST", null))
        assertFalse(FcmStreamControlActions.isSwitchCamera("STREAM_CONTROL", "STOP"))
        assertFalse(FcmStreamControlActions.isSwitchCamera(null, null))
    }

    @Test
    fun dropsDuplicateFlipWithinWindow() {
        FcmStreamControlActions.resetForTests()
        assertTrue(FcmStreamControlActions.acceptFlip(1_000L))
        assertFalse(FcmStreamControlActions.acceptFlip(1_000L + 2_000L))
        assertTrue(FcmStreamControlActions.acceptFlip(1_000L + FcmStreamControlActions.DEDUP_WINDOW_MS))
    }
}
