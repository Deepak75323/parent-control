package com.childmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraOccupantHintTest {

    @Test
    fun encodeForFailureReason_prefersLabelAndSanitizesSpaces() {
        val encoded =
            CameraOccupantHint.encodeForFailureReason(
                CameraOccupantHint.Hint(packageName = "com.google.android.apps.tachyon", label = "Google Meet"),
            )
        assertEquals("Google_Meet", encoded)
    }

    @Test
    fun encodeForFailureReason_fallsBackToPackage() {
        val encoded =
            CameraOccupantHint.encodeForFailureReason(
                CameraOccupantHint.Hint(packageName = "com.whatsapp", label = null),
            )
        assertEquals("com.whatsapp", encoded)
    }

    @Test
    fun encodeForFailureReason_nullWhenEmpty() {
        assertNull(
            CameraOccupantHint.encodeForFailureReason(
                CameraOccupantHint.Hint(packageName = null, label = null),
            ),
        )
    }

    @Test
    fun failureReason_includesEncodedHint() {
        assertEquals(
            "CAMERA_IN_USE_BY_OTHER_APP:WhatsApp",
            CameraOccupantHint.failureReason(
                CameraOccupantHint.Hint(packageName = "com.whatsapp", label = "WhatsApp"),
            ),
        )
        assertEquals(
            "CAMERA_IN_USE_BY_OTHER_APP",
            CameraOccupantHint.failureReason(CameraOccupantHint.Hint(null, null)),
        )
    }
}
