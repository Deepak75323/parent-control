package com.childmonitor.platform

import android.content.Context
import com.childmonitor.MonitoringPrefs

/**
 * Runtime feature flags for Android 16 migration — toggled via debug or remote config later.
 */
object Android16FeatureFlags {

    private const val PREFS = "android16_feature_flags"

    private const val KEY_STRICT_PROJECTION_GATE = "strict_projection_gate"
    private const val KEY_EDGE_TO_EDGE = "edge_to_edge"
    private const val KEY_EXTENDED_FGS_DELAY = "extended_fgs_delay"
    private const val KEY_WORKMANAGER_RECOVERY = "workmanager_recovery"
    private const val KEY_BOOT_FGS_BLOCK = "boot_fgs_block"

    fun isStrictProjectionGateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_STRICT_PROJECTION_GATE, true)

    fun isEdgeToEdgeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EDGE_TO_EDGE, AndroidPlatform.enforcesEdgeToEdge())

    fun isExtendedFgsDelayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EXTENDED_FGS_DELAY, AndroidPlatform.isAndroid16OrLater())

    fun isWorkManagerRecoveryEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WORKMANAGER_RECOVERY, true)

    fun isBootFgsBlockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BOOT_FGS_BLOCK, AndroidPlatform.blocksMediaProjectionFgsFromBoot())

    fun setFlag(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(key, enabled).apply()
    }

    fun snapshot(context: Context): Map<String, Boolean> = mapOf(
        KEY_STRICT_PROJECTION_GATE to isStrictProjectionGateEnabled(context),
        KEY_EDGE_TO_EDGE to isEdgeToEdgeEnabled(context),
        KEY_EXTENDED_FGS_DELAY to isExtendedFgsDelayEnabled(context),
        KEY_WORKMANAGER_RECOVERY to isWorkManagerRecoveryEnabled(context),
        KEY_BOOT_FGS_BLOCK to isBootFgsBlockEnabled(context),
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
