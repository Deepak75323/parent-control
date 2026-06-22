package com.childmonitor.oem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.childmonitor.MonitoringPrefs

/**
 * Guides users through battery optimization / autostart whitelisting on aggressive OEMs.
 * Does not bypass restrictions — opens the correct system settings once after pairing.
 */
object OemPowerWhitelistHelper {

    private const val TAG = "OemPowerWhitelist"
    private const val PREFS = "oem_power_prefs"
    private const val KEY_PROMPTED = "battery_prompt_done"
    private const val KEY_AUTOSTART_PROMPTED = "miui_autostart_prompt_done"

    fun ensureBatteryOptimizationExemption(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_PROMPTED, false)
        ) {
            return
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
            markPrompted(context)
            return
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            markPrompted(context)
            Log.i(TAG, "Requested battery optimization exemption")
        } catch (e: Exception) {
            Log.w(TAG, "Battery optimization intent failed — trying OEM-specific", e)
            if (!openOemBatterySettings(context)) {
                openAppDetails(context)
            }
            markPrompted(context)
        }
    }

    /** Route to vendor-specific background / autostart settings. */
    fun promptOemBackgroundSettingsIfNeeded(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) return
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTOSTART_PROMPTED, false)
        ) {
            return
        }
        markAutostartPrompted(context)
        when (OemVendor.detect()) {
            OemVendor.XIAOMI -> openXiaomiAutostart(context)
            OemVendor.SAMSUNG -> openSamsungNeverSleepingApps(context)
            OemVendor.OPPO, OemVendor.REALME -> openOppoAutostart(context)
            OemVendor.VIVO -> openVivoBackground(context)
            OemVendor.ONEPLUS -> openOnePlusOptimization(context)
            OemVendor.MOTOROLA -> openMotorolaBattery(context)
            OemVendor.PIXEL, OemVendor.GENERIC -> openAppDetails(context)
        }
    }

    /** @deprecated Use promptOemBackgroundSettingsIfNeeded */
    fun promptMiuiAutostartIfNeeded(context: Context) {
        promptOemBackgroundSettingsIfNeeded(context)
    }

    private fun openOemBatterySettings(context: Context): Boolean {
        return when (OemVendor.detect()) {
            OemVendor.SAMSUNG -> openSamsungBattery(context)
            OemVendor.OPPO, OemVendor.REALME -> tryIntents(context, listOf(
                Intent().setClassName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity",
                ),
            ))
            OemVendor.VIVO -> tryIntents(context, listOf(
                Intent().setClassName(
                    "com.iqoo.powersaving",
                    "com.iqoo.powersaving.PowerSavingManagerActivity",
                ),
            ))
            OemVendor.ONEPLUS -> tryIntents(context, listOf(
                Intent().setClassName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                ),
            ))
            else -> false
        }
    }

    private fun openXiaomiAutostart(context: Context) {
        val intents = listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings",
            ),
        )
        if (!tryIntents(context, intents)) openAppDetails(context)
    }

    private fun openSamsungNeverSleepingApps(context: Context) {
        val intents = listOf(
            Intent().setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity",
            ),
            Intent().setClassName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            ),
        )
        if (!tryIntents(context, intents)) openAppDetails(context)
    }

    private fun openSamsungBattery(context: Context): Boolean {
        openSamsungNeverSleepingApps(context)
        return true
    }

    private fun openOppoAutostart(context: Context) {
        val intents = listOf(
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            ),
            Intent().setClassName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity",
            ),
        )
        if (!tryIntents(context, intents)) openAppDetails(context)
    }

    private fun openVivoBackground(context: Context) {
        val intents = listOf(
            Intent().setClassName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            ),
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ),
        )
        if (!tryIntents(context, intents)) openAppDetails(context)
    }

    private fun openOnePlusOptimization(context: Context) {
        val intents = listOf(
            Intent().setClassName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            ),
        )
        if (!tryIntents(context, intents)) openAppDetails(context)
    }

    private fun openMotorolaBattery(context: Context) {
        val intents = listOf(
            Intent().setClassName(
                "com.motorola.ccc",
                "com.motorola.ccc.main.MainActivity",
            ),
        )
        if (!tryIntents(context, intents)) openAppDetails(context)
    }

    private fun tryIntents(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Opened OEM settings: ${intent.component}")
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open app details", e)
        }
    }

    private fun markAutostartPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTOSTART_PROMPTED, true)
            .apply()
        OemSetupValidator.markAutostartPromptCompleted(context)
    }

    private fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PROMPTED, true)
            .apply()
    }
}
