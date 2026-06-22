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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setStatusBarDisabled(admin, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "enableKiosk policy failed", e)
            return false
        }
        MonitoringPrefs.setKioskEnabled(context, true)
        val intent = android.content.Intent(context, KioskActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        return true
    }

    fun disableKiosk(context: Context): Boolean {
        MonitoringPrefs.setKioskEnabled(context, false)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = adminComponent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isDeviceOwner(context)) {
                dpm.setStatusBarDisabled(admin, false)
            }
            if (isDeviceOwner(context)) {
                dpm.setLockTaskPackages(admin, emptyArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "disableKiosk policy failed", e)
        }
        val intent = android.content.Intent(context, KioskActivity::class.java).apply {
            action = KioskActivity.ACTION_DISABLE
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    fun isKioskActive(context: Context): Boolean = MonitoringPrefs.isKioskEnabled(context)
}
