package com.childmonitor

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.deviceowner.DeviceOwnerManager
import com.childmonitor.tamper.TamperApiClient
import com.childmonitor.tamper.TamperGateController

/**
 * Device administrator for anti-tampering. Uninstall requires deactivating this admin first.
 * Launcher hiding uses [TamperProtectionManager] (works with Device Admin; stronger lock-down
 * needs Device Owner provisioning via ADB — see child/README.md).
 */
class ChildDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        MonitoringPrefs.clearTamperUnlock(context)
        // Refresh grace so post-activate Settings "Deactivate" page does not open TamperGate.
        MonitoringPrefs.beginDeviceAdminSetupGrace(context)
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

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        TamperGateController.onDisableRequested(context)
        return context.getString(R.string.tamper_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        MonitoringPrefs.clearTamperUnlock(context)
        Thread {
            TamperApiClient.reportAdminDisabled(context.applicationContext)
        }.start()
        TamperProtectionManager.onDeviceAdminDisabled(context)
        Log.i(TAG, "Device administrator deactivated")
    }

    companion object {
        private const val TAG = "ChildDeviceAdmin"
    }
}
