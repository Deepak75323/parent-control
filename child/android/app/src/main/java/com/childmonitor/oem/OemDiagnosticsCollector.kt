package com.childmonitor.oem

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.childmonitor.AccessibilityHealthChecker
import com.childmonitor.AppBlockingAccessibilityService
import com.childmonitor.MonitoringPrefs
import com.childmonitor.capture.MediaProjectionConsentState
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.deviceowner.DeviceOwnerManager
import com.childmonitor.platform.Android16ReadinessEvaluator
import com.childmonitor.platform.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified OEM + platform diagnostics for heartbeats, validation dumps, and readiness scoring.
 */
object OemDiagnosticsCollector {

    fun collect(context: Context): Map<String, Any?> {
        val vendor = OemVendor.detect()
        val batteryExempt = OemSetupValidator.isBatteryOptimizationExempt(context)
        val autostartPromptDone = OemSetupValidator.isAutostartPromptCompleted(context)
        val notificationsGranted = notificationPermissionGranted(context)
        val overlayGranted = Settings.canDrawOverlays(context)
        val a11yEnabled = AccessibilityHealthChecker.isAccessibilityEnabled(context)
        val a11yConnected = AppBlockingAccessibilityService.instance != null
        val projectionState = MediaProjectionTokenStore.getConsentState(context)
        val projectionGranted = projectionState == MediaProjectionConsentState.GRANTED_ACTIVE
        val workManagerReady = isWorkManagerAvailable(context)
        val exactAlarmsAllowed = canScheduleExactAlarms(context)
        val android16Report = Android16ReadinessEvaluator.evaluate(context)
        val platformCaps = AndroidPlatform.capabilities(context)

        return linkedMapOf(
            "vendor" to vendor.id,
            "vendorDisplay" to vendor.displayName,
            "manufacturer" to (Build.MANUFACTURER ?: "unknown"),
            "brand" to (Build.BRAND ?: "unknown"),
            "model" to (Build.MODEL ?: "unknown"),
            "osRelease" to (Build.VERSION.RELEASE ?: "unknown"),
            "sdkInt" to Build.VERSION.SDK_INT,
            "miuiVersion" to getSystemProperty("ro.miui.ui.version.name"),
            "hyperOsVersion" to getSystemProperty("ro.mi.os.version.name"),
            "oneUiVersion" to getSystemProperty("ro.build.version.oneui"),
            "colorOsVersion" to getSystemProperty("ro.build.version.oplusrom"),
            "batteryExempt" to batteryExempt,
            "autostartPromptDone" to autostartPromptDone,
            "oemSetupComplete" to OemSetupValidator.isOemSetupComplete(context),
            "notificationPermissionGranted" to notificationsGranted,
            "overlayGranted" to overlayGranted,
            "accessibilityEnabled" to a11yEnabled,
            "accessibilityServiceConnected" to a11yConnected,
            "mediaProjectionGranted" to projectionGranted,
            "mediaProjectionConsentState" to projectionState.name,
            "deviceOwnerActive" to DeviceOwnerManager.isDeviceOwner(context),
            "fcmTokenPresent" to !MonitoringPrefs.fcmToken(context).isNullOrBlank(),
            "workManagerAvailable" to workManagerReady,
            "exactAlarmsAllowed" to exactAlarmsAllowed,
            "paired" to MonitoringPrefs.isPaired(context),
            "readinessScore" to OemSetupValidator.computeReadinessScore(context),
            "android16ReadinessScore" to android16Report.score,
            "android16Blockers" to android16Report.blockers,
            "android16Warnings" to android16Report.warnings,
            "android16Checks" to android16Report.checks,
            "platformCapabilities" to platformCaps,
            "failureClassifications" to OemSetupValidator.classifyFailures(context),
        )
    }

    fun toJson(context: Context): JSONObject {
        val map = collect(context)
        val json = JSONObject()
        for ((key, value) in map) {
            when (value) {
                is List<*> -> json.put(key, JSONArray(value))
                else -> json.put(key, value)
            }
        }
        return json
    }

    private fun notificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isWorkManagerAvailable(context: Context): Boolean {
        return try {
            WorkManager.getInstance(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, key, "") as String).ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
