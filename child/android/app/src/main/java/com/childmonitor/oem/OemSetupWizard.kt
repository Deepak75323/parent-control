package com.childmonitor.oem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.childmonitor.capture.FullScreenIntentHelper

/**
 * Guided OEM setup wizard — step list, verification, and settings deep-links.
 * MIUI/HyperOS need several manual toggles that cannot be read programmatically;
 * those steps require an explicit parent/child confirmation after visiting Settings.
 */
object OemSetupWizard {

    private const val TAG = "OemSetupWizard"
    private const val PREFS = "oem_setup_wizard_prefs"
    private const val KEY_AUTOSTART_CONFIRMED = "wizard_autostart_confirmed"
    private const val KEY_BATTERY_SAVER_CONFIRMED = "wizard_battery_saver_confirmed"
    private const val KEY_RECENTS_LOCK_CONFIRMED = "wizard_recents_lock_confirmed"

    data class Step(
        val id: String,
        val title: String,
        val description: String,
        val instructions: List<String>,
        val autoVerifiable: Boolean,
    )

    fun steps(context: Context): List<Step> {
        val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
        val vendor = OemSetupValidator.vendor()
        val list = mutableListOf<Step>()

        if (OemSetupValidator.requiresBatteryExemption()) {
            list.add(
                Step(
                    id = "battery_exempt",
                    title = "Turn off battery optimization",
                    description = "Android must not put $appLabel to sleep in the background.",
                    instructions = listOf(
                        "Tap Open settings below.",
                        "Choose Don't optimize or Allow for $appLabel.",
                        "Return here — we'll check automatically.",
                    ),
                    autoVerifiable = true,
                ),
            )
        }

        if (OemSetupValidator.requiresAutostartPrompt()) {
            list.add(
                Step(
                    id = "autostart",
                    title = when (vendor) {
                        OemVendor.XIAOMI -> "Enable Autostart (MIUI / HyperOS)"
                        OemVendor.OPPO, OemVendor.REALME -> "Enable Auto launch"
                        OemVendor.VIVO -> "Allow background running"
                        else -> "Allow background start"
                    },
                    description = "Without this, monitoring stops a few minutes after you leave the app.",
                    instructions = OemCompatibilityManager.buildGuidance(context).steps,
                    autoVerifiable = false,
                ),
            )
        }

        if (vendor == OemVendor.XIAOMI) {
            list.add(
                Step(
                    id = "battery_saver",
                    title = "Set Battery saver to No restrictions",
                    description = "MIUI limits background camera, mic, and screen capture separately from autostart.",
                    instructions = listOf(
                        "Tap Open settings → find $appLabel.",
                        "Open Battery saver (or Battery).",
                        "Select No restrictions.",
                        "Return here and tap I've done this.",
                    ),
                    autoVerifiable = false,
                ),
            )
            list.add(
                Step(
                    id = "recents_lock",
                    title = "Lock app in recent apps",
                    description = "Prevents MIUI from swiping the monitor away when cleaning memory.",
                    instructions = listOf(
                        "Open recent apps (square button).",
                        "Find $appLabel, pull down or long-press its card.",
                        "Tap the lock icon so the app stays in memory.",
                        "Return here and tap I've done this.",
                    ),
                    autoVerifiable = false,
                ),
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !FullScreenIntentHelper.isGranted(context)
        ) {
            list.add(
                Step(
                    id = "fullscreen_intent",
                    title = "Allow full-screen notifications",
                    description = "Required on Android 14+ so live monitoring can start when the app is in the background.",
                    instructions = listOf(
                        "Tap Open settings below.",
                        "Enable Allow full-screen intent notifications for $appLabel.",
                        "Return here — we'll check automatically.",
                    ),
                    autoVerifiable = true,
                ),
            )
        }

        if (list.isEmpty()) {
            list.add(
                Step(
                    id = "battery_exempt",
                    title = "Battery optimization",
                    description = "Optional on this phone — skip if already unrestricted.",
                    instructions = listOf(
                        "Tap Open settings if needed.",
                        "Set $appLabel to Don't optimize.",
                    ),
                    autoVerifiable = true,
                ),
            )
        }

        return list
    }

    fun isStepComplete(context: Context, stepId: String): Boolean = when (stepId) {
        "battery_exempt" ->
            !OemSetupValidator.requiresBatteryExemption() ||
                OemSetupValidator.isBatteryOptimizationExempt(context)
        "autostart" ->
            !OemSetupValidator.requiresAutostartPrompt() ||
                prefs(context).getBoolean(KEY_AUTOSTART_CONFIRMED, false) ||
                OemSetupValidator.isAutostartPromptCompleted(context)
        "battery_saver" ->
            vendor() != OemVendor.XIAOMI ||
                prefs(context).getBoolean(KEY_BATTERY_SAVER_CONFIRMED, false)
        "recents_lock" ->
            vendor() != OemVendor.XIAOMI ||
                prefs(context).getBoolean(KEY_RECENTS_LOCK_CONFIRMED, false)
        "fullscreen_intent" ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                FullScreenIntentHelper.isGranted(context)
            } else {
                true
            }
        else -> true
    }

    fun isWizardComplete(context: Context): Boolean =
        steps(context).all { isStepComplete(context, it.id) }

    fun openStep(context: Context, stepId: String) {
        val appContext = context.applicationContext
        when (stepId) {
            "battery_exempt" -> openBatteryExemption(appContext)
            "autostart" -> OemCompatibilityManager.buildGuidance(appContext)
                .autostartIntent?.let { launch(appContext, it) }
                ?: openAppDetails(appContext)
            "battery_saver" -> openAppDetails(appContext)
            "recents_lock" -> {
                // No settings screen — show instructions only; optional nudge to recents via home.
                Log.i(TAG, "recents_lock is manual-only")
            }
            "fullscreen_intent" -> FullScreenIntentHelper.openSettings(appContext)
            else -> openAppDetails(appContext)
        }
    }

    fun confirmManualStep(context: Context, stepId: String) {
        val editor = prefs(context).edit()
        when (stepId) {
            "autostart" -> {
                editor.putBoolean(KEY_AUTOSTART_CONFIRMED, true)
                OemSetupValidator.markAutostartPromptCompleted(context)
            }
            "battery_saver" -> editor.putBoolean(KEY_BATTERY_SAVER_CONFIRMED, true)
            "recents_lock" -> editor.putBoolean(KEY_RECENTS_LOCK_CONFIRMED, true)
        }
        editor.apply()
        Log.i(TAG, "manual step confirmed: $stepId")
    }

    private fun openBatteryExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "battery exemption intent failed", e)
            openAppDetails(context)
        }
    }

    private fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "app details failed", e)
        }
    }

    private fun launch(context: Context, intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "OEM intent failed: ${intent.component}", e)
            openAppDetails(context)
        }
    }

    private fun vendor() = OemSetupValidator.vendor()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
