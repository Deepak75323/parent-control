package com.childmonitor.capture

import android.content.pm.ServiceInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundServiceCrashGuardTest {

    @Test
    fun cameraPromotionTypesPreferCameraWhenGranted() {
        assertArrayEquals(
            intArrayOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA),
            ForegroundServiceCrashGuard.cameraPromotionTypes(hasCameraPermission = true),
        )
    }

    @Test
    fun cameraPromotionTypesEmptyWhenPermissionMissing() {
        // Do not fall through to microphone — RECORD_AUDIO may be denied.
        assertEquals(
            0,
            ForegroundServiceCrashGuard.cameraPromotionTypes(hasCameraPermission = false).size,
        )
    }

    @Test
    fun audioPromotionTypesUseMicrophoneOnApi34() {
        assertArrayEquals(
            intArrayOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE),
            ForegroundServiceCrashGuard.audioPromotionTypes(),
        )
    }

    @Test
    @Config(sdk = [33])
    fun legacyApiUsesEmptyTypedPromotionArrays() {
        assertEquals(0, ForegroundServiceCrashGuard.cameraPromotionTypes(true).size)
        assertEquals(0, ForegroundServiceCrashGuard.audioPromotionTypes().size)
    }
}
