package com.childmonitor.platform

import android.content.pm.ServiceInfo
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static manifest compliance checks for Android 16 FGS type requirements.
 */
class Android16ManifestComplianceTest {

    @Test
    fun requiredFgsTypeConstants_present() {
        val required = Android16ReadinessEvaluator.REQUIRED_FGS_TYPES
        assertTrue(required.contains(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        assertTrue(required.contains(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION))
        assertTrue(required.contains(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA))
        assertTrue(required.contains(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE))
    }
}
