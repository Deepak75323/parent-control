package com.childmonitor

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.deviceowner.DeviceOwnerManager

/**
 * Device administrator for anti-tampering. Uninstall requires deactivating this admin first.
 * Launcher hiding uses [TamperProtectionManager] (works with Device Admin; stronger lock-down
 * needs Device Owner provisioning via ADB — see child-app/README.md).
 */
class ChildDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        MonitoringPrefs.setTamperProtectionEnabled(context, true)
        if (DeviceOwnerManager.isDeviceOwner(context)) {
            DeviceOwnerManager.onDeviceOwnerEstablished(context)
        }
        if (MonitoringPrefs.isHideLauncherEnabled(context)) {
            TamperProtectionManager.setHideLauncher(context, true)
        }
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        DeviceOwnerManager.onDeviceOwnerEstablished(context)
        Log.i(TAG, "Device Owner provisioning complete")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        TamperProtectionManager.onDeviceAdminDisabled(context)
        Log.i(TAG, "Device administrator deactivated")
    }

    companion object {
        private const val TAG = "ChildDeviceAdmin"
    }
}
