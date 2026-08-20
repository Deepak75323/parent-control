package com.childmonitor.telemetry

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.constants.ApiPaths
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Batches UI analytics events (impression / click / ingress / error / back) and uploads
 * to the backend client analytics ingest API.
 */
object ClientAnalyticsReporter {

    private const val TAG = "ClientAnalytics"
    private const val PREFS = "client_analytics_reporter"
    private const val MAX_BATCH = 25
    private const val MAX_QUEUE = 200

    private val queue = ConcurrentLinkedQueue<JSONObject>()

    fun record(
        eventType: String,
        section: String,
        subSection: String? = null,
        eventLabel: String? = null,
        screen: String? = null,
        attributes: JSONObject = JSONObject(),
    ) {
        val payload = JSONObject()
            .put("eventType", eventType)
            .put("section", section)
            .put("timestampMs", System.currentTimeMillis())
        subSection?.let { payload.put("subSection", it) }
        eventLabel?.let { payload.put("eventLabel", it) }
        screen?.let { payload.put("screen", it) }
        if (attributes.length() > 0) {
            payload.put("attributes", attributes)
        }
        if (queue.size >= MAX_QUEUE) {
            queue.poll()
        }
        queue.offer(payload)
    }

    fun recordIngress(action: String, attributes: JSONObject = JSONObject()) {
        record(
            eventType = "ingress",
            section = "fcm",
            subSection = "wake",
            eventLabel = "ingress",
            attributes = attributes.put("action", action),
        )
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
        val ok = DeviceHttpClient.postJson(context, ApiPaths.DEVICE_ANALYTICS_BATCH, body.toString())
        if (!ok) {
            Log.w(TAG, "analytics batch upload failed — events dropped")
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
