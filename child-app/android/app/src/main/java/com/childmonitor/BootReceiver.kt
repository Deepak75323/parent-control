package com.childmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.childmonitor.monitoring.BootWakeChain

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        // Handle standard boot, direct-boot (credential-encrypted phase), and
        // Xiaomi/MIUI quick-boot (MIUI power-off charging recovery events).
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||    // Xiaomi quick-boot
            action == "com.htc.intent.action.QUICKBOOT_POWERON"       // HTC quick-boot
        if (!isBoot) return
        if (!MonitoringPrefs.isPaired(context)) return

        BootWakeChain.schedule(context.applicationContext, BootWakeChain.Trigger.BOOT)

        TamperProtectionManager.applyStoredTamperSettings(context)
        Thread {
            com.childmonitor.oem.OemPowerWhitelistHelper
                .ensureBatteryOptimizationExemption(context.applicationContext)
            com.childmonitor.oem.OemPowerWhitelistHelper
                .promptOemBackgroundSettingsIfNeeded(context.applicationContext)
            AccessibilityHealthChecker.reportIfDisabled(context.applicationContext)
        }.start()
    }
}
