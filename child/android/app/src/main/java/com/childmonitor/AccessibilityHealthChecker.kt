package com.childmonitor

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

/** Verifies critical permissions after reboot; reports violations to backend. */
object AccessibilityHealthChecker {

    private const val TAG = "AccessibilityHealth"

    /**
     * True when Clock Time accessibility service is enabled.
     * MIUI/HyperOS may lag updating [Settings.Secure.ACCESSIBILITY_ENABLED] — rely on
     * [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] and live service connection.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val component = ComponentName(
            context.packageName,
            AppBlockingAccessibilityService::class.java.name,
        ).flattenToString()

        // Trust system settings only — do not use live service instance (stays true briefly after disable).
        return enabledServices.split(':').any { segment ->
            segment.equals(component, ignoreCase = true)
        }
    }

    fun isServiceConnected(): Boolean = AppBlockingAccessibilityService.instance != null

    fun reportIfDisabled(context: Context) {
        if (isAccessibilityEnabled(context)) return
        Log.w(TAG, "Accessibility service disabled after boot — reporting violation")
        PolicyEnforcementEngine.reportViolation(
            context,
            "ACCESSIBILITY_DISABLED",
            null,
            null,
        )
    }
}
