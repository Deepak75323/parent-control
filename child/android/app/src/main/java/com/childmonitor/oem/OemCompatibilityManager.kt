package com.childmonitor.oem

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * A1 fix: OEM-specific compatibility layer for battery optimisation and autostart.
 *
 * Background: Xiaomi (MIUI/HyperOS), OPPO, Vivo, Realme, and OnePlus all ship
 * custom battery managers that aggressively kill foreground services after 2–5 minutes
 * unless the user explicitly adds the app to the "autostart" or "protected apps" list.
 * The standard Android REQUEST_IGNORE_BATTERY_OPTIMIZATIONS API is often insufficient
 * on these OEMs.
 *
 * This file provides:
 *  1. [requiresOemAutostartGuidance] — returns true when the current device is known
 *     to require manual autostart configuration.
 *  2. [buildAutostartIntent] — returns an Intent to the OEM-specific autostart settings
 *     page, or null when the OEM is unknown / the page doesn't exist on this firmware.
 *  3. [buildBatteryOptimizationIntent] — returns an Intent to the standard or OEM
 *     battery optimisation exemption page.
 *  4. [getOemDescription] — human-readable setup instructions for the parent-facing
 *     setup wizard.
 *
 * All Intent launches must be wrapped in try/catch by the caller; OEM intents are
 * highly firmware-version-dependent and may not resolve on all ROM versions.
 *
 * Testing matrix:
 *   Xiaomi Redmi 10 (MIUI 13), OPPO A96 (ColorOS 12), Vivo Y75 (OriginOS 2),
 *   Realme C35 (Realme UI 3), OnePlus Nord 2 (OxygenOS 11), Samsung Galaxy A54 (OneUI 5)
 */
object OemCompatibilityManager {

    private const val TAG = "OemCompatibilityManager"

    enum class OemType {
        XIAOMI_MIUI,
        XIAOMI_HYPEROS,
        OPPO_COLOROS,
        VIVO_ORIGINOS,
        REALME_UI,
        ONEPLUS_OXYGENOS,
        SAMSUNG_ONEUI,
        HUAWEI_EMUI,
        GENERIC_ANDROID,
    }

    data class OemGuidance(
        val oemType: OemType,
        val requiresManualAction: Boolean,
        val autostartIntent: Intent?,
        val batteryOptIntent: Intent,
        val title: String,
        val steps: List<String>,
        val warningNote: String?,
    )

    // ─── Public API ──────────────────────────────────────────────────────────────

    fun detectOem(): OemType {
        val mfr = Build.MANUFACTURER.lowercase().trim()
        val brand = Build.BRAND.lowercase().trim()
        return when {
            mfr == "xiaomi" || brand == "xiaomi" || brand == "redmi" || brand == "poco" -> {
                if (isHyperOs()) OemType.XIAOMI_HYPEROS else OemType.XIAOMI_MIUI
            }
            mfr == "oppo" || brand == "oppo" -> OemType.OPPO_COLOROS
            mfr == "vivo" || brand == "vivo" -> OemType.VIVO_ORIGINOS
            mfr == "realme" || brand == "realme" -> OemType.REALME_UI
            mfr == "oneplus" || brand == "oneplus" -> OemType.ONEPLUS_OXYGENOS
            mfr == "samsung" || brand == "samsung" -> OemType.SAMSUNG_ONEUI
            mfr == "huawei" || brand == "huawei" || brand == "honor" -> OemType.HUAWEI_EMUI
            else -> OemType.GENERIC_ANDROID
        }
    }

    /**
     * True when the device OEM is known to kill foreground services without manual
     * autostart / protected-apps configuration.
     */
    fun requiresOemAutostartGuidance(context: Context): Boolean {
        return when (detectOem()) {
            OemType.XIAOMI_MIUI,
            OemType.XIAOMI_HYPEROS,
            OemType.OPPO_COLOROS,
            OemType.VIVO_ORIGINOS,
            OemType.REALME_UI,
            OemType.ONEPLUS_OXYGENOS,
            OemType.HUAWEI_EMUI -> true
            OemType.SAMSUNG_ONEUI -> {
                // Samsung follows Android standard — REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                // is usually sufficient.  Return true only if already in restricted mode.
                isInBatteryOptimization(context)
            }
            OemType.GENERIC_ANDROID -> isInBatteryOptimization(context)
        }
    }

    /**
     * Build OEM-specific guidance for the setup wizard.
     * Never throws — any Intent that cannot be built is returned as null inside [OemGuidance].
     */
    fun buildGuidance(context: Context): OemGuidance {
        val oem = detectOem()
        val batteryIntent = buildBatteryOptimizationIntent(context)
        return when (oem) {
            OemType.XIAOMI_MIUI -> OemGuidance(
                oemType = oem,
                requiresManualAction = true,
                autostartIntent = buildXiaomiAutostartIntent(context),
                batteryOptIntent = batteryIntent,
                title = "Xiaomi MIUI — Enable Autostart",
                steps = listOf(
                    "Open Security app (pre-installed on your device)",
                    "Tap 'Manage apps' or 'App Manager'",
                    "Search for '${context.applicationInfo.loadLabel(context.packageManager)}'",
                    "Tap 'Autostart' and enable it",
                    "Also tap 'Battery saver' → 'No restrictions'",
                ),
                warningNote = "Without Autostart enabled, MIUI will stop monitoring within 2–3 minutes after you leave the app.",
            )
            OemType.XIAOMI_HYPEROS -> OemGuidance(
                oemType = oem,
                requiresManualAction = true,
                autostartIntent = buildHyperOsAutostartIntent(context),
                batteryOptIntent = batteryIntent,
                title = "Xiaomi HyperOS — Enable Autostart",
                steps = listOf(
                    "Open Settings → Apps → Manage apps",
                    "Find '${context.applicationInfo.loadLabel(context.packageManager)}'",
                    "Tap 'Autostart' and enable the toggle",
                    "Go back → Battery saver → No restrictions",
                ),
                warningNote = "HyperOS has stricter background limits than MIUI. Both Autostart AND battery exemption are required.",
            )
            OemType.OPPO_COLOROS -> OemGuidance(
                oemType = oem,
                requiresManualAction = true,
                autostartIntent = buildOppoAutostartIntent(context),
                batteryOptIntent = batteryIntent,
                title = "OPPO ColorOS — Enable Auto Launch",
                steps = listOf(
                    "Open Settings → Battery → App Quick Freeze",
                    "Make sure this app is NOT frozen",
                    "Open Settings → Apps → App Management",
                    "Find this app → Battery → Allow background activity",
                    "Also enable: App Management → Auto-launch",
                ),
                warningNote = "ColorOS disables background launch by default. Both Auto-launch and background activity are required.",
            )
            OemType.VIVO_ORIGINOS -> OemGuidance(
                oemType = oem,
                requiresManualAction = true,
                autostartIntent = buildVivoAutostartIntent(context),
                batteryOptIntent = batteryIntent,
                title = "Vivo OriginOS — Enable Background Running",
                steps = listOf(
                    "Open iManager app (pre-installed)",
                    "Tap 'App Management' → 'Memory Cleanup'",
                    "Find this app and tap 'Lock' to prevent cleanup",
                    "Also open Settings → Battery → Background Power Consumption",
                    "Set this app to 'No restrictions'",
                ),
                warningNote = "Vivo's iManager automatically terminates background apps. Locking the app prevents this.",
            )
            OemType.REALME_UI -> OemGuidance(
                oemType = oem,
                requiresManualAction = true,
                autostartIntent = buildRealmeAutostartIntent(context),
                batteryOptIntent = batteryIntent,
                title = "Realme UI — Enable Autolaunch",
                steps = listOf(
                    "Open Phone Manager app",
                    "Tap 'App Boost' or 'App Management'",
                    "Find this app → Enable 'Allow Auto Launch'",
                    "Also: Settings → Battery → App Battery Usage → No restrictions",
                ),
                warningNote = "Realme UI (based on ColorOS) has the same restrictions as OPPO.",
            )
            OemType.ONEPLUS_OXYGENOS -> OemGuidance(
                oemType = oem,
                requiresManualAction = true,
                autostartIntent = buildOnePlusAutostartIntent(context),
                batteryOptIntent = batteryIntent,
                title = "OnePlus OxygenOS — Enable Auto Launch",
                steps = listOf(
                    "Open Settings → Battery → Battery Optimization",
                    "Find this app → Select 'Don't optimize'",
                    "Also: Settings → Apps → App Management → this app → Battery → Allow background activity",
                ),
                warningNote = "OnePlus is less aggressive than Xiaomi/OPPO, but battery optimization should still be disabled.",
            )
            OemType.SAMSUNG_ONEUI -> OemGuidance(
                oemType = oem,
                requiresManualAction = isInBatteryOptimization(context),
                autostartIntent = null, // Samsung uses standard Android API
                batteryOptIntent = batteryIntent,
                title = "Samsung OneUI — Battery Optimization",
                steps = listOf(
                    "Open Settings → Apps → this app → Battery",
                    "Select 'Unrestricted'",
                ),
                warningNote = null,
            )
            OemType.HUAWEI_EMUI -> OemGuidance(
                oemType = oem,
                requiresManualAction = true,
                autostartIntent = buildHuaweiAutostartIntent(context),
                batteryOptIntent = batteryIntent,
                title = "Huawei EMUI — Enable Protected Apps",
                steps = listOf(
                    "Open Settings → Battery → App Launch",
                    "Find this app → Disable 'Manage automatically'",
                    "Enable: Auto-launch, Secondary launch, Run in background",
                ),
                warningNote = "Huawei devices do not have Google Play Services. Push notifications (FCM) are not available on Huawei. Commands will be polled every 15 seconds instead.",
            )
            OemType.GENERIC_ANDROID -> OemGuidance(
                oemType = oem,
                requiresManualAction = isInBatteryOptimization(context),
                autostartIntent = null,
                batteryOptIntent = batteryIntent,
                title = "Battery Optimization",
                steps = listOf(
                    "Open Settings → Battery → Battery Optimization",
                    "Find this app → Select 'Don't optimize'",
                ),
                warningNote = null,
            )
        }
    }

    /**
     * Returns an Intent that opens the standard Android battery optimization exemption dialog.
     * Safe on all API levels ≥ 23.
     */
    fun buildBatteryOptimizationIntent(context: Context): Intent {
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    fun isInBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ─── OEM-specific intent builders ───────────────────────────────────────────

    private fun buildXiaomiAutostartIntent(context: Context): Intent? {
        val candidates = listOf(
            // MIUI 12/13
            "com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity",
            // Older MIUI
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            // Legacy
            "com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsManager",
        )
        return resolveFirst(context, candidates) ?: run {
            // Fallback: generic Security Centre
            buildPackageIntent(context, "com.miui.securitycenter")
        }
    }

    private fun buildHyperOsAutostartIntent(context: Context): Intent? {
        val candidates = listOf(
            "com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity",
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        )
        return resolveFirst(context, candidates)
            ?: buildPackageIntent(context, "com.miui.securitycenter")
    }

    private fun buildOppoAutostartIntent(context: Context): Intent? {
        val candidates = listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.coloros.oppoguardelf" to "com.coloros.powermanager.powersaving.PowerUseStratagy",
        )
        return resolveFirst(context, candidates)
            ?: buildPackageIntent(context, "com.coloros.safecenter")
            ?: buildPackageIntent(context, "com.oppo.safe")
    }

    private fun buildVivoAutostartIntent(context: Context): Intent? {
        val candidates = listOf(
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
        )
        return resolveFirst(context, candidates)
            ?: buildPackageIntent(context, "com.iqoo.secure")
    }

    private fun buildRealmeAutostartIntent(context: Context): Intent? {
        // Realme UI is based on ColorOS
        val candidates = listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        )
        return resolveFirst(context, candidates)
            ?: buildOppoAutostartIntent(context)
    }

    private fun buildOnePlusAutostartIntent(context: Context): Intent? {
        val candidates = listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        )
        return resolveFirst(context, candidates)
            ?: buildPackageIntent(context, "com.oneplus.security")
    }

    private fun buildHuaweiAutostartIntent(context: Context): Intent? {
        val candidates = listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        )
        return resolveFirst(context, candidates)
            ?: buildPackageIntent(context, "com.huawei.systemmanager")
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun resolveFirst(context: Context, candidates: List<Pair<String, String>>): Intent? {
        val pm = context.packageManager
        for ((pkg, cls) in candidates) {
            try {
                val intent = Intent().setClassName(pkg, cls)
                if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    Log.d(TAG, "Resolved OEM intent: $pkg/$cls")
                    return intent
                }
            } catch (_: Exception) {
                // This OEM class does not exist on this ROM version — try next candidate.
            }
        }
        return null
    }

    private fun buildPackageIntent(context: Context, packageName: String): Intent? {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) launchIntent else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true when the device runs Xiaomi HyperOS (as opposed to MIUI).
     *
     * P2-6: The original implementation relied solely on SystemProperties reflection, which
     * can be blocked on hardened ROMs or future Android versions that restrict hidden APIs.
     * A silent failure returned `false`, causing HyperOS devices to receive MIUI guidance
     * that lacks the "Autostart AND battery exemption are required" warning — causing setup
     * failures on HyperOS devices where one toggle alone is insufficient.
     *
     * Fix: try reflection first; if it throws or returns blank, fall back to inspecting
     * [Build.DISPLAY] and [Build.HOST] for "hyperos" strings, which Xiaomi embeds in HyperOS
     * build fingerprints (e.g. "HyperOS1.0.7.0.VNDCNXM").
     */
    private fun isHyperOs(): Boolean {
        // Primary: SystemProperties reflection — present on all Xiaomi ROMs to date.
        try {
            val prop = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, "ro.mi.os.version.incremental") as? String
            if (!prop.isNullOrBlank()) {
                Log.d(TAG, "isHyperOs: detected via SystemProperties ro.mi.os.version.incremental=$prop")
                return true
            }
        } catch (_: Exception) {
            // Reflection blocked on this ROM — fall through to Build.DISPLAY check.
            Log.d(TAG, "isHyperOs: SystemProperties reflection unavailable, trying Build fields")
        }
        // Fallback: HyperOS build fingerprints contain "HyperOS" in Build.DISPLAY or Build.HOST.
        val display = Build.DISPLAY.lowercase()
        val host = Build.HOST.lowercase()
        val fromBuild = display.contains("hyperos") || host.contains("hyperos")
        if (fromBuild) {
            Log.d(TAG, "isHyperOs: detected via Build.DISPLAY='${Build.DISPLAY}' Build.HOST='${Build.HOST}'")
        }
        return fromBuild
    }
}
