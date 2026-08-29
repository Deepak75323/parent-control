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
        if (!shouldOpenTamperGateForTarget(context, target)) {
            Log.i(TAG, "Ignoring tamper bypass — protection off, unpaired, unlocked, or setup in progress")
            if (MonitoringPrefs.isTamperUnlocked(context) && !afterUnlock.isNullOrBlank()) {
                openAfterUnlock(context, afterUnlock)
            } else if (target == ACTION_ACCESSIBILITY &&
                !MonitoringPrefs.isAccessibilityTamperProtectionEnabled(context)
            ) {
                openAfterUnlock(context, ACTION_ACCESSIBILITY)
            }
            return
            return
        }
        launchGate(context, afterUnlock, target)
    }

    fun launchGate(context: Context, afterUnlock: String? = null, target: String? = null) {
        if (!shouldOpenTamperGateForTarget(context, target)) {
            Log.i(TAG, "Skipping tamper gate — protection off, unpaired, unlocked, or setup in progress")
            return
        }
        val intent = buildGateIntent(context, afterUnlock, target)
        // Prefer AccessibilityService context on MIUI/HyperOS — plain app context is often
        // blocked by background-activity launch limits, so the gate never appears.
        if (com.childmonitor.AppBlockingAccessibilityService.tryStartActivity(intent)) {
            return
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startActivity failed for tamper gate", e)
        }
    }

    fun launchGateForAction(context: Context, action: String?) {
        if (MonitoringPrefs.isTamperUnlocked(context)) {
            openAfterUnlock(context, action)
            return
        }
        if (!shouldOpenTamperGateForTarget(context, action)) {
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
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
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
        return TamperBlockPolicy.shouldBlockSettingsInteraction(
            deviceAdminActive = TamperProtectionManager.isDeviceAdminActive(context),
            paired = MonitoringPrefs.isPaired(context),
            tamperProtectionEnabled = MonitoringPrefs.isTamperProtectionEnabled(context),
            tamperUnlocked = MonitoringPrefs.isTamperUnlocked(context),
            permissionSetupInProgress = MonitoringPrefs.isPermissionSetupInProgress(context),
            deviceAdminSetupGraceActive = MonitoringPrefs.isDeviceAdminSetupGraceActive(context),
        )
    }

    fun shouldBlockAccessibilityDisable(context: Context): Boolean {
        return TamperBlockPolicy.shouldBlockAccessibilityDisable(
            accessibilityEnabled = AccessibilityHealthChecker.isAccessibilityEnabled(context),
            accessibilityTamperEnabled = MonitoringPrefs.isAccessibilityTamperProtectionEnabled(context),
            paired = MonitoringPrefs.isPaired(context),
            tamperUnlocked = MonitoringPrefs.isTamperUnlocked(context),
            permissionSetupInProgress = MonitoringPrefs.isPermissionSetupInProgress(context),
        )
    }

    private fun shouldOpenTamperGateForTarget(context: Context, target: String?): Boolean {
        val paired = MonitoringPrefs.isPaired(context)
        val tamperUnlocked = MonitoringPrefs.isTamperUnlocked(context)
        val setupInProgress = MonitoringPrefs.isPermissionSetupInProgress(context)
        return if (target == ACTION_ACCESSIBILITY) {
            TamperBlockPolicy.shouldOpenAccessibilityTamperGate(
                accessibilityTamperEnabled = MonitoringPrefs.isAccessibilityTamperProtectionEnabled(context),
                paired = paired,
                tamperUnlocked = tamperUnlocked,
                permissionSetupInProgress = setupInProgress,
            )
        } else {
            TamperBlockPolicy.shouldOpenDeviceAdminTamperGate(
                deviceAdminTamperEnabled = MonitoringPrefs.isTamperProtectionEnabled(context),
                paired = paired,
                tamperUnlocked = tamperUnlocked,
                permissionSetupInProgress = setupInProgress,
                deviceAdminSetupGraceActive = MonitoringPrefs.isDeviceAdminSetupGraceActive(context),
            )
        }
    }
}
