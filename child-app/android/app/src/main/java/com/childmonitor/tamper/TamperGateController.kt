package com.childmonitor.tamper

import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.AccessibilityHealthChecker
import com.childmonitor.MonitoringPrefs
import com.childmonitor.TamperProtectionManager
import com.childmonitor.permissions.PermissionSettingsLauncher

object TamperGateController {

    private const val TAG = "TamperGate"

    const val EXTRA_AFTER_UNLOCK = "after_unlock"
    const val EXTRA_TARGET = "target"
    const val ACTION_ACCESSIBILITY = "accessibility"
    const val ACTION_DEVICE_ADMIN = "device_admin"

    fun onDisableRequested(context: Context, target: String? = ACTION_DEVICE_ADMIN) {
        onTamperBypassRequested(context, null, target)
    }

    fun onAccessibilityDisableRequested(context: Context) {
        onTamperBypassRequested(context, ACTION_ACCESSIBILITY, ACTION_ACCESSIBILITY)
    }

    private fun onTamperBypassRequested(context: Context, afterUnlock: String?, target: String?) {
        if (MonitoringPrefs.isTamperUnlocked(context)) {
            Log.i(TAG, "Tamper bypass allowed — unlock token active")
            if (!afterUnlock.isNullOrBlank()) {
                openAfterUnlock(context, afterUnlock)
            }
            return
        }
        Thread {
            TamperApiClient.reportDisableAttempt(context.applicationContext, target)
        }.start()
        launchGate(context, afterUnlock, target)
    }

    fun launchGate(context: Context, afterUnlock: String? = null, target: String? = null) {
        val intent = buildGateIntent(context, afterUnlock, target)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startActivity failed, trying a11y", e)
            com.childmonitor.AppBlockingAccessibilityService.tryStartActivity(intent)
        }
    }

    fun launchGateForAction(context: Context, action: String?) {
        if (MonitoringPrefs.isTamperUnlocked(context)) {
            openAfterUnlock(context, action)
            return
        }
        launchGate(context, action, action)
    }

    fun buildGateIntent(
        context: Context,
        afterUnlock: String? = null,
        target: String? = null,
    ): Intent =
        Intent(context, TamperGateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (!afterUnlock.isNullOrBlank()) {
                putExtra(EXTRA_AFTER_UNLOCK, afterUnlock)
            }
            if (!target.isNullOrBlank()) {
                putExtra(EXTRA_TARGET, target)
            }
        }

    fun openAfterUnlock(context: Context, action: String?) {
        when (action) {
            ACTION_ACCESSIBILITY -> PermissionSettingsLauncher.openAccessibility(context, null)
            ACTION_DEVICE_ADMIN -> PermissionSettingsLauncher.openDeviceAdmin(context, null)
        }
    }

    fun shouldBlockSettingsInteraction(context: Context): Boolean {
        if (!TamperProtectionManager.isDeviceAdminActive(context)) return false
        if (!MonitoringPrefs.isTamperProtectionEnabled(context)) return false
        return !MonitoringPrefs.isTamperUnlocked(context)
    }

    fun shouldBlockAccessibilityDisable(context: Context): Boolean {
        if (!AccessibilityHealthChecker.isAccessibilityEnabled(context)) return false
        return shouldBlockSettingsInteraction(context)
    }
}
