package com.childmonitor.monitoring

import android.content.Context
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.telemetry.MobileTelemetryReporter
import org.json.JSONObject

/**
 * Boot recovery telemetry — structured events for adb / future analytics pipeline.
 * Filter: `adb logcat -s PCP:BOOT_RECOVERY BootWakeChain`
 */
object BootRecoveryTelemetry {

    private const val TAG = "PCP:BOOT_RECOVERY"
    private const val PREFS = "boot_recovery_telemetry"

    fun recordBootReceived(context: Context, trigger: String) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("boot_received_at_ms", now)
            .putString("last_trigger", trigger)
            .putInt("recovery_success_count", 0)
            .apply()
        emit(context, "boot_received", JSONObject().put("trigger", trigger))
    }

    fun recordPhaseStart(context: Context, phase: BootRecoveryPhase, trigger: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_phase", phase.name)
            .putLong("last_phase_start_ms", System.currentTimeMillis())
            .apply()
        emit(
            context,
            "phase_start",
            JSONObject()
                .put("phase", phase.name)
                .put("trigger", trigger),
        )
    }

    fun recordPhaseComplete(
        context: Context,
        phase: BootRecoveryPhase,
        success: Boolean,
        commandsExecuted: Int,
        lagMs: Long,
        detail: String? = null,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putLong("last_phase_complete_ms", System.currentTimeMillis())
            .putBoolean("last_phase_success", success)
            .putInt("last_commands_executed", commandsExecuted)
            .putLong("last_command_lag_ms", lagMs)
        if (success) {
            editor.putLong("last_recovery_success_ms", System.currentTimeMillis())
            editor.putInt(
                "recovery_success_count",
                prefs.getInt("recovery_success_count", 0) + 1,
            )
        }
        editor.apply()
        emit(
            context,
            if (success) "phase_success" else "phase_failed",
            JSONObject()
                .put("phase", phase.name)
                .put("commandsExecuted", commandsExecuted)
                .put("commandLagMs", lagMs)
                .put("detail", detail ?: ""),
        )
    }

    fun bootReceivedAtMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("boot_received_at_ms", 0L)

    fun isWithinPostBootWindow(context: Context, windowMs: Long = 10 * 60_000L): Boolean {
        val bootAt = bootReceivedAtMs(context)
        if (bootAt <= 0L) return false
        return System.currentTimeMillis() - bootAt < windowMs
    }

    fun lastEventJson(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("last_event_json", null)

    private fun emit(context: Context, event: String, payload: JSONObject) {
        val envelope = JSONObject()
            .put("event", event)
            .put("ts", System.currentTimeMillis())
            .put("payload", payload)
        val line = envelope.toString()
        Log.i(TAG, line)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.BOOT_RECOVERY,
            event,
            payload.toString(),
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_event_json", line)
            .apply()
        val attrs = JSONObject()
        payload.keys().forEachRemaining { key ->
            attrs.put(key, payload.get(key))
        }
        if (event == "phase_success" || event == "phase_failed") {
            attrs.put("success", event == "phase_success")
        }
        MobileTelemetryReporter.record("boot_recovery", event, attrs)
    }
}
