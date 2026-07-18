package com.childmonitor.sync

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.oem.OemVendor
import org.json.JSONObject

/**
 * Structured recovery telemetry — logged locally for adb/field diagnostics.
 * Events use a stable JSON shape for future analytics pipeline ingestion.
 *
 * All events include OEM vendor, Android API level, and battery-optimization status so
 * field issues can be correlated to specific device families without manual repro.
 */
object MediaProjectionRecoveryTelemetry {

    private const val TAG = "PCP:MEDIA_PROJECTION"
    private const val PREFS = "media_projection_telemetry"
    private const val KEY_LAST_EVENT = "last_event_json"
    private const val KEY_RECOVERY_ATTEMPTS = "recovery_attempts"
    private const val KEY_LAST_RECOVERY_MS = "last_recovery_ms"
    private const val KEY_LAST_LOSS_REASON = "last_loss_reason"
    private const val KEY_LOSS_COUNT = "loss_count"

    // ─── Write helpers ────────────────────────────────────────────────────────

    fun recordLoss(context: Context, reason: String, consentState: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_LOSS_REASON, reason)
            .putInt(KEY_LOSS_COUNT, prefs.getInt(KEY_LOSS_COUNT, 0) + 1)
            .apply()

        emit(
            context,
            "projection_loss",
            JSONObject()
                .put("reason", reason)
                .put("consentState", consentState)
                .put("lossCount", prefs.getInt(KEY_LOSS_COUNT, 1)),
        )
    }

    fun recordRecoveryAttempt(context: Context, reason: String, attempt: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_RECOVERY_ATTEMPTS, attempt)
            .putLong(KEY_LAST_RECOVERY_MS, System.currentTimeMillis())
            .apply()
        emit(
            context,
            "recovery_attempt",
            JSONObject()
                .put("reason", reason)
                .put("attempt", attempt),
        )
    }

    fun recordRecoveryOutcome(context: Context, success: Boolean, reason: String) {
        emit(
            context,
            if (success) "recovery_success" else "recovery_failed",
            JSONObject().put("reason", reason),
        )
    }

    fun recordRestored(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_RECOVERY_ATTEMPTS, 0)
            .apply()
        emit(context, "projection_restored", JSONObject())
    }

    /**
     * Clears the recovery attempt budget without emitting a "restored" event.
     * Used when Screen is re-requested after WorkManager burned attempts waiting on
     * Accessibility (Xiaomi force-stop) so Cast can be shown again.
     */
    fun clearRecoveryAttemptBudget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_RECOVERY_ATTEMPTS, 0)
            .apply()
    }

    /**
     * Record a low-memory warning from the FGS. This event is emitted opportunistically —
     * the process may be killed before the log is flushed, but SharedPreferences.commit()
     * is synchronous so at least the prefs entry is written.
     */
    fun recordLowMemoryWarning(context: Context, activeProjection: Boolean) {
        emit(
            context,
            "low_memory_warning",
            JSONObject()
                .put("activeProjection", activeProjection)
                .put("riskLevel", if (activeProjection) "high" else "low"),
        )
    }

    /**
     * Record task removal (user swiped app from Recents). On many OEMs this results in the
     * process being killed shortly after, invalidating the projection binder.
     */
    fun recordTaskRemoved(context: Context, activeProjection: Boolean) {
        emit(
            context,
            "task_removed",
            JSONObject()
                .put("activeProjection", activeProjection),
        )
    }

    /**
     * Record boot event with projection state for post-mortem analysis.
     */
    fun recordBootWithLostConsent(context: Context, consentState: String) {
        emit(
            context,
            "boot_with_lost_consent",
            JSONObject()
                .put("consentState", consentState),
        )
    }

    // ─── Read helpers ─────────────────────────────────────────────────────────

    fun lastEventJson(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_EVENT, null)

    fun recoveryAttemptCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_RECOVERY_ATTEMPTS, 0)

    fun lossCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LOSS_COUNT, 0)

    fun lastLossReason(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_LOSS_REASON, null)

    /**
     * Full diagnostics snapshot — call from debug screens or crash reporters.
     * Does not mutate prefs.
     */
    fun fullDiagnostics(context: Context): Map<String, Any?> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oem = OemVendor.detect()
        val isBatteryOptimized = isBatteryOptimized(context)
        return mapOf(
            "oem" to oem.id,
            "oemDisplayName" to oem.displayName,
            "apiLevel" to Build.VERSION.SDK_INT,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "batteryOptimized" to isBatteryOptimized,
            "recoveryAttempts" to prefs.getInt(KEY_RECOVERY_ATTEMPTS, 0),
            "lossCount" to prefs.getInt(KEY_LOSS_COUNT, 0),
            "lastLossReason" to prefs.getString(KEY_LAST_LOSS_REASON, null),
            "lastRecoveryMs" to prefs.getLong(KEY_LAST_RECOVERY_MS, 0L),
            "lastEvent" to prefs.getString(KEY_LAST_EVENT, null),
        )
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun emit(context: Context, event: String, payload: JSONObject) {
        val oem = OemVendor.detect()
        val envelope = JSONObject()
            .put("event", event)
            .put("ts", System.currentTimeMillis())
            .put("api", Build.VERSION.SDK_INT)
            .put("oem", oem.id)
            .put("batteryOptimized", isBatteryOptimized(context))
            .put("payload", payload)
        val line = envelope.toString()
        Log.i(TAG, line)
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, event, payload.toString())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_EVENT, line)
            .apply()
    }

    /**
     * Returns true when the app is under battery optimization (Doze / App Standby restriction).
     * Battery optimization can prevent background activity launches and FGS starts on MIUI,
     * Xiaomi HyperOS, Samsung, and stock Android 6+.
     */
    private fun isBatteryOptimized(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
