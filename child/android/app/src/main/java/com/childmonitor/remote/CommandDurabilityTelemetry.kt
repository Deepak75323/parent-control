package com.childmonitor.remote

import android.content.Context
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.telemetry.MobileTelemetryReporter
import org.json.JSONObject

/**
 * Structured telemetry for command durability — filter: `adb logcat -s PCP:COMMAND_DURABILITY`
 */
object CommandDurabilityTelemetry {

    private const val TAG = "PCP:COMMAND_DURABILITY"
    private const val PREFS = "command_durability_telemetry"

    fun recordEnqueued(context: Context, commandId: String, queueDepth: Int) {
        bumpCounter(context, "enqueued_total")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("queue_depth", queueDepth)
            .putLong("last_enqueue_ms", System.currentTimeMillis())
            .apply()
        emit(context, "enqueued", JSONObject().put("commandId", commandId).put("queueDepth", queueDepth))
    }

    fun recordDrained(context: Context, count: Int) {
        emit(context, "drained", JSONObject().put("count", count))
    }

    fun recordDeadLetter(context: Context, commandId: String, reason: String) {
        bumpCounter(context, "dead_letter_total")
        emit(context, "dead_letter", JSONObject().put("commandId", commandId).put("reason", reason))
    }

    fun recordRecovery(context: Context, trigger: String, replayedClaims: Int, flushedBuffer: Int) {
        bumpCounter(context, "recovery_total")
        val lagMs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("last_enqueue_ms", 0L)
            .let { if (it > 0L) System.currentTimeMillis() - it else 0L }
        emit(
            context,
            "recovery",
            JSONObject()
                .put("trigger", trigger)
                .put("replayedClaims", replayedClaims)
                .put("flushedBuffer", flushedBuffer)
                .put("commandLagMs", lagMs),
        )
    }

    fun recordReplay(context: Context, commandId: String, source: String) {
        emit(context, "replay", JSONObject().put("commandId", commandId).put("source", source))
    }

    fun lastEventJson(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("last_event_json", null)

    private fun bumpCounter(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun emit(context: Context, event: String, payload: JSONObject) {
        val envelope = JSONObject()
            .put("event", event)
            .put("ts", System.currentTimeMillis())
            .put("payload", payload)
        val line = envelope.toString()
        Log.i(TAG, line)
        PcpFlowLogger.log(PcpFlowLogger.Category.COMMAND_DURABILITY, event, payload.toString())
        MobileTelemetryReporter.record("command_durability", event, payload)
    }
}
