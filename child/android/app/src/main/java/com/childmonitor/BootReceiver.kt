package com.childmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.childmonitor.capture.MediaProjectionConsentState
import com.childmonitor.capture.MediaProjectionLossHandler
import com.childmonitor.capture.MediaProjectionTokenStore
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

        if (MonitoringPrefs.isPaired(context)) {
            BootWakeChain.schedule(context.applicationContext, BootWakeChain.Trigger.BOOT)
            TamperProtectionManager.applyStoredTamperSettings(context)
            Thread {
                handlePostBootChecks(context.applicationContext)
            }.start()
        } else {
            SetupAccessNotificationHelper.sync(context.applicationContext)
        }
    }

    private fun handlePostBootChecks(context: Context) {
        com.childmonitor.oem.OemPowerWhitelistHelper
            .ensureBatteryOptimizationExemption(context)
        com.childmonitor.oem.OemPowerWhitelistHelper
            .promptOemBackgroundSettingsIfNeeded(context)
        AccessibilityHealthChecker.reportIfDisabled(context)

        // ── MediaProjection consent state on boot ─────────────────────────────
        // After any reboot (or low-memory kill + restart), the in-memory projection
        // binder is gone. Detect this and schedule a recovery WorkManager job so
        // the child can silently re-grant consent before the parent next tries to stream.
        //
        // We call getConsentState() here to trigger the GRANTED_ACTIVE → LOST_AFTER_PROCESS_DEATH
        // state transition, which writes the new state to SharedPreferences so the heartbeat
        // (already scheduled by BootWakeChain) sends the correct consentState to the backend.
        val consentState = MediaProjectionTokenStore.getConsentState(context)
        when (consentState) {
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionConsentState.RECOVERY_DEFERRED,
            MediaProjectionConsentState.RECOVERY_FAILED,
            -> MediaProjectionLossHandler.onProjectionLostOnBoot(context, consentState)
            // NEVER_GRANTED, GRANTED_ACTIVE, RECOVERY_PENDING — no action needed on boot.
            else -> Unit
        }
    }
}
