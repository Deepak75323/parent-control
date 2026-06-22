package com.childmonitor.oem

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.childmonitor.AccessibilityHealthChecker
import com.childmonitor.MonitoringPrefs

/**
 * Runtime validation for OEM-specific setup steps and device readiness scoring.
 * Autostart cannot be verified programmatically on most OEMs — we track user prompt completion.
 */
object OemSetupValidator {

    private const val PREFS = "oem_power_prefs"
    private const val KEY_AUTOSTART_PROMPTED = "miui_autostart_prompt_done"

    fun vendor(): OemVendor = OemVendor.detect()

    fun isXiaomiFamily(): Boolean = vendor() == OemVendor.XIAOMI

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isAutostartPromptCompleted(context: Context): Boolean {
        if (!requiresAutostartPrompt()) return true
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOSTART_PROMPTED, false)
    }

    fun markAutostartPromptCompleted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTOSTART_PROMPTED, true)
            .apply()
    }

    /** OEMs known to kill background apps without autostart / battery whitelist. */
    fun requiresAutostartPrompt(): Boolean {
        return when (vendor()) {
            OemVendor.XIAOMI,
            OemVendor.OPPO,
            OemVendor.VIVO,
            OemVendor.REALME,
            OemVendor.ONEPLUS,
            -> true
            OemVendor.SAMSUNG,
            OemVendor.MOTOROLA,
            -> true
            OemVendor.PIXEL,
            OemVendor.GENERIC,
            -> false
        }
    }

    fun requiresBatteryExemption(): Boolean {
        return when (vendor()) {
            OemVendor.PIXEL,
            OemVendor.GENERIC,
            -> false
            else -> true
        }
    }

    fun isOemSetupComplete(context: Context): Boolean =
        OemSetupWizard.isWizardComplete(context)

    /**
     * Device readiness score 0–100 for fleet health and certification pre-gates.
     * Security trust is separate (DeviceTrustEngine).
     */
    fun computeReadinessScore(context: Context): Int {
        var score = 0
        if (MonitoringPrefs.isPaired(context)) score += 10
        if (isBatteryOptimizationExempt(context) || !requiresBatteryExemption()) score += 15
        if (isAutostartPromptCompleted(context) || !requiresAutostartPrompt()) score += 15
        if (AccessibilityHealthChecker.isAccessibilityEnabled(context)) score += 20
        val projectionOk = com.childmonitor.capture.MediaProjectionTokenStore
            .getConsentState(context) ==
            com.childmonitor.capture.MediaProjectionConsentState.GRANTED_ACTIVE
        if (projectionOk) score += 15
        if (!MonitoringPrefs.fcmToken(context).isNullOrBlank()) score += 10
        if (isOemSetupComplete(context)) score += 15
        return score.coerceIn(0, 100)
    }

    /** Failure classifications for telemetry and support triage. */
    fun classifyFailures(context: Context): List<String> {
        val failures = mutableListOf<String>()
        if (requiresBatteryExemption() && !isBatteryOptimizationExempt(context)) {
            failures.add("BATTERY_NOT_EXEMPT")
        }
        if (requiresAutostartPrompt() && !isAutostartPromptCompleted(context)) {
            failures.add("AUTOSTART_NOT_CONFIRMED")
        }
        if (!AccessibilityHealthChecker.isAccessibilityEnabled(context)) {
            failures.add("ACCESSIBILITY_DISABLED")
        }
        val consent = com.childmonitor.capture.MediaProjectionTokenStore.getConsentState(context)
        if (consent != com.childmonitor.capture.MediaProjectionConsentState.GRANTED_ACTIVE) {
            failures.add("MEDIA_PROJECTION_NOT_ACTIVE")
        }
        if (MonitoringPrefs.fcmToken(context).isNullOrBlank()) {
            failures.add("FCM_TOKEN_MISSING")
        }
        return failures
    }

    fun diagnostics(context: Context): Map<String, Any?> = OemDiagnosticsCollector.collect(context)

    fun openOemBackgroundSettings(context: Context) {
        OemPowerWhitelistHelper.promptOemBackgroundSettingsIfNeeded(context.applicationContext)
    }

    /** @deprecated Use openOemBackgroundSettings */
    fun openMiuiAutostartSettings(context: Context) {
        openOemBackgroundSettings(context)
    }
}
