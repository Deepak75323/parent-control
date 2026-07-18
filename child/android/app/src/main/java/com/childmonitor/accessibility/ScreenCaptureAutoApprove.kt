package com.childmonitor.accessibility

import android.content.Context
import android.provider.Settings
import com.childmonitor.MonitoringPrefs
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.capture.ScreenCaptureController

/**
 * Readiness checks for Accessibility-based MediaProjection auto-approve.
 */
object ScreenCaptureAutoApprove {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.contains(context.packageName)
    }

    /**
     * True when Accessibility can auto-tap "Share screen" during consent.
     * Enabled in Settings is not enough — the service must be connected (not crashed).
     */
    fun isReady(context: Context): Boolean =
        isAccessibilityEnabled(context) &&
            com.childmonitor.AccessibilityHealthChecker.isServiceConnected()

    /**
     * True when parent screen share can run without child interaction:
     * valid in-memory token, or Accessibility ready to auto-approve re-consent.
     */
    fun isSilentOrAutoApproveReady(context: Context): Boolean =
        ScreenCaptureController.hasLiveProjection() || isReady(context)

    fun armForConsent(context: Context, windowMs: Long = 60_000L) {
        MonitoringPrefs.armScreenCaptureAutoApprove(context, windowMs)
        MediaProjectionConsentAutomation.clearLoggedPackages()
    }

    fun disarm(context: Context) {
        MonitoringPrefs.disarmScreenCaptureAutoApprove(context)
    }
}
