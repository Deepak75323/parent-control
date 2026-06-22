package com.childmonitor

import android.content.Context
import android.provider.Settings
import android.util.Log

/** Verifies critical permissions after reboot; reports violations to backend. */
object AccessibilityHealthChecker {

    private const val TAG = "AccessibilityHealth"

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        return enabled?.contains(context.packageName) == true
    }

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
