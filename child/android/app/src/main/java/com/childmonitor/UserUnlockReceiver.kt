package com.childmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.monitoring.BootRecoveryTelemetry
import com.childmonitor.monitoring.BootWakeChain
import com.childmonitor.monitoring.MonitoringServiceLauncher

/**
 * Fires when the user unlocks the device — credential-encrypted storage and network
 * are often unavailable at raw BOOT_COMPLETED on Android 10+.
 */
class UserUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_USER_UNLOCKED && action != Intent.ACTION_USER_PRESENT) return
        if (!MonitoringPrefs.isPaired(context)) return
        if (!BootRecoveryTelemetry.isWithinPostBootWindow(context)) return

        Log.i(TAG, "User unlock — reinforcing boot wake chain action=$action")
        BootWakeChain.schedule(context.applicationContext, BootWakeChain.Trigger.USER_UNLOCK)
        MonitoringServiceLauncher.requestMonitoring(
            context.applicationContext,
            MonitoringServiceLauncher.Reason.USER_UNLOCK,
        )
    }

    companion object {
        private const val TAG = "UserUnlockReceiver"
    }
}
