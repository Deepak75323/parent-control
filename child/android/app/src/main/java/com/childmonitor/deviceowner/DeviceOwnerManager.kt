package com.childmonitor.deviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.childmonitor.ChildDeviceAdminReceiver
import com.childmonitor.MonitoringPrefs

object DeviceOwnerManager {

    private const val TAG = "DeviceOwnerManager"

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, ChildDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(context))
    }

    /** Apply policies after Device Owner is set (ADB or QR provisioning). */
    fun onDeviceOwnerEstablished(context: Context) {
        if (!isDeviceOwner(context)) return
        MonitoringPrefs.setDeviceOwnerProvisioned(context, true)
        applyOwnerPolicies(context)
        Log.i(TAG, "Device Owner policies applied")
    }

    fun applyOwnerPolicies(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = adminComponent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setUninstallBlocked(admin, context.packageName, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "setUninstallBlocked failed", e)
        }
        grantCoreRuntimePermissions(context)
    }

    private fun grantCoreRuntimePermissions(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = adminComponent(context)
        val permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
        for (permission in permissions) {
            try {
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    dpm.setPermissionGrantState(
                        admin,
                        context.packageName,
                        permission,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "grant $permission failed", e)
            }
        }
    }

    fun getAdbProvisionCommand(context: Context): String =
        "adb shell dpm set-device-owner ${adminComponent(context).flattenToString()}"

    fun clearDeviceOwnerState(context: Context) {
        disableKiosk(context)
        MonitoringPrefs.setDeviceOwnerProvisioned(context, false)
        MonitoringPrefs.setMediaProjectionGranted(context, false)
        MonitoringPrefs.setKioskEnabled(context, false)
        com.childmonitor.capture.MediaProjectionTokenStore.clear(context)
    }

    fun enableKiosk(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = adminComponent(context)
        try {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            // P2-06: harden lock-task — block notifications / system info / home escape hatches.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setStatusBarDisabled(admin, true)
                val features =
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                        DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                // Allow only none of the above (0) — fully locked task.
                dpm.setLockTaskFeatures(admin, 0)
                Log.i(TAG, "P2-06 lockTaskFeatures applied (blockedMask=$features)")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setKeyguardDisabledFeatures(
                    admin,
                    DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "enableKiosk policy failed", e)
            return false
        }
        MonitoringPrefs.setKioskEnabled(context, true)
        return launchKioskActivity(context, enable = true)
    }

    fun disableKiosk(context: Context): Boolean {
        MonitoringPrefs.setKioskEnabled(context, false)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = adminComponent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isDeviceOwner(context)) {
                dpm.setStatusBarDisabled(admin, false)
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isDeviceOwner(context)) {
                dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE)
            }
            if (isDeviceOwner(context)) {
                dpm.setLockTaskPackages(admin, emptyArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "disableKiosk policy failed", e)
        }
        return launchKioskActivity(context, enable = false)
    }

    /**
     * P2-06: BAL-safe kiosk launch — prefer foreground activity context; fall back to NEW_TASK.
     */
    private fun launchKioskActivity(context: Context, enable: Boolean): Boolean {
        val intent = android.content.Intent(context, KioskActivity::class.java).apply {
            if (!enable) action = KioskActivity.ACTION_DISABLE
            addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "kiosk activity launch failed (BAL?) enable=$enable", e)
            false
        }
    }

    fun isKioskActive(context: Context): Boolean = MonitoringPrefs.isKioskEnabled(context)
}
