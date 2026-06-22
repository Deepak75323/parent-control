package com.childmonitor.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.childmonitor.AccessibilityHealthChecker
import com.childmonitor.MonitoringPrefs
import com.childmonitor.capture.MediaProjectionConsentState
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.oem.OemSetupValidator

/**
 * Android 16 migration readiness score (0–100) for fleet gating and certification.
 */
object Android16ReadinessEvaluator {

    data class Report(
        val score: Int,
        val blockers: List<String>,
        val warnings: List<String>,
        val checks: Map<String, Boolean>,
    )

    fun evaluate(context: Context): Report {
        val checks = linkedMapOf<String, Boolean>()
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        checks["paired"] = MonitoringPrefs.isPaired(context)
        if (!checks["paired"]!!) blockers.add("NOT_PAIRED")

        checks["burstFgsPath"] = AndroidPlatform.useBurstFgs()
        if (!checks["burstFgsPath"]!! && Build.VERSION.SDK_INT >= AndroidPlatform.SDK_ANDROID_16) {
            blockers.add("LEGACY_FGS_ON_API36")
        }

        checks["mediaProjectionConsent"] =
            MediaProjectionTokenStore.getConsentState(context) ==
                MediaProjectionConsentState.GRANTED_ACTIVE
        if (!checks["mediaProjectionConsent"]!!) warnings.add("MEDIA_PROJECTION_INACTIVE")

        checks["accessibilityEnabled"] = AccessibilityHealthChecker.isAccessibilityEnabled(context)
        if (!checks["accessibilityEnabled"]!!) blockers.add("ACCESSIBILITY_DISABLED")

        checks["cameraPermission"] =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        checks["micPermission"] =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!checks["cameraPermission"]!!) warnings.add("CAMERA_NOT_GRANTED")
        if (!checks["micPermission"]!!) warnings.add("MIC_NOT_GRANTED")

        checks["notificationPermission"] = notificationGranted(context)
        if (!checks["notificationPermission"]!!) warnings.add("NOTIFICATIONS_NOT_GRANTED")

        checks["oemSetupComplete"] = OemSetupValidator.isOemSetupComplete(context)
        if (!checks["oemSetupComplete"]!!) warnings.add("OEM_SETUP_INCOMPLETE")

        checks["workManagerRecovery"] =
            com.childmonitor.monitoring.MonitoringWorkScheduler.isScheduled(context)
        if (!checks["workManagerRecovery"]!!) warnings.add("WORKMANAGER_NOT_SCHEDULED")

        checks["manifestFgsTypesDeclared"] = true // validated by Android16ManifestComplianceTest
        checks["projectionGateReady"] = true // MediaProjectionCaptureGate present

        var score = 0
        if (checks["paired"] == true) score += 10
        if (checks["burstFgsPath"] == true) score += 15
        if (checks["mediaProjectionConsent"] == true) score += 15
        if (checks["accessibilityEnabled"] == true) score += 15
        if (checks["cameraPermission"] == true) score += 5
        if (checks["micPermission"] == true) score += 5
        if (checks["notificationPermission"] == true) score += 10
        if (checks["oemSetupComplete"] == true) score += 10
        if (checks["workManagerRecovery"] == true) score += 10
        if (checks["manifestFgsTypesDeclared"] == true) score += 5

        return Report(
            score = score.coerceIn(0, 100),
            blockers = blockers,
            warnings = warnings,
            checks = checks,
        )
    }

    private fun notificationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** Manifest FGS type constants we must declare for compliance audit. */
    val REQUIRED_FGS_TYPES: Set<Int> = setOf(
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
    )
}
