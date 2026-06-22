package com.childmonitor.sync

import android.content.Context
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import org.json.JSONObject

/**
 * Structured recovery telemetry — logged locally for adb/field diagnostics.
 * Events use a stable JSON shape for future analytics pipeline ingestion.
 */
object MediaProjectionRecoveryTelemetry {

    private const val TAG = "PCP:MEDIA_PROJECTION"
    private const val PREFS = "media_projection_telemetry"
    private const val KEY_LAST_EVENT = "last_event_json"
    private const val KEY_RECOVERY_ATTEMPTS = "recovery_attempts"
    private const val KEY_LAST_RECOVERY_MS = "last_recovery_ms"

    fun recordLoss(context: Context, reason: String, consentState: String) {
        emit(
            context,
            "projection_loss",
            JSONObject()
                .put("reason", reason)
                .put("consentState", consentState),
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

    fun lastEventJson(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_EVENT, null)

    fun recoveryAttemptCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_RECOVERY_ATTEMPTS, 0)

    private fun emit(context: Context, event: String, payload: JSONObject) {
        val envelope = JSONObject()
            .put("event", event)
            .put("ts", System.currentTimeMillis())
            .put("payload", payload)
        val line = envelope.toString()
        Log.i(TAG, line)
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, event, payload.toString())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_EVENT, line)
            .apply()
    }
}
