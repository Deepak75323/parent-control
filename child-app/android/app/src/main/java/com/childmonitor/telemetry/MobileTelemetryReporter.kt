package com.childmonitor.telemetry

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Batches on-device telemetry events and uploads to the backend observability ingest API.
 */
object MobileTelemetryReporter {

    private const val TAG = "MobileTelemetry"
    private const val PREFS = "mobile_telemetry_reporter"
    private const val MAX_BATCH = 25
    private const val MAX_QUEUE = 200

    private val queue = ConcurrentLinkedQueue<JSONObject>()

    fun record(category: String, event: String, attributes: JSONObject = JSONObject()) {
        val payload = JSONObject()
            .put("category", category)
            .put("event", event)
            .put("timestampMs", System.currentTimeMillis())
            .put("attributes", attributes)
        if (queue.size >= MAX_QUEUE) {
            queue.poll()
        }
        queue.offer(payload)
    }

    fun flush(context: Context) {
        if (!MonitoringPrefs.isPaired(context) || queue.isEmpty()) {
            return
        }
        val batch = JSONArray()
        repeat(minOf(MAX_BATCH, queue.size)) {
            queue.poll()?.let { batch.put(it) }
        }
        if (batch.length() == 0) {
            return
        }
        val body = JSONObject()
            .put("correlationId", PcpFlowLogger.correlationId ?: "")
            .put("collectorVersion", "1.0.0")
            .put("events", batch)
        val ok = DeviceHttpClient.postJson(context, "/api/v1/device/telemetry/batch", body.toString())
        if (!ok) {
            Log.w(TAG, "telemetry batch upload failed — events dropped")
        }
    }

    fun flushIfDue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong("last_flush_ms", 0L)
        val now = System.currentTimeMillis()
        if (now - last < 60_000L) {
            return
        }
        flush(context)
        prefs.edit().putLong("last_flush_ms", now).apply()
    }
}
