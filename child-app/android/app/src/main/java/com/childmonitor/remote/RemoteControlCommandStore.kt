package com.childmonitor.remote

import android.content.Context
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Disk-backed queue for remote-control commands — survives process death, reboot, and OEM kill.
 * Replaces the in-memory-only [RemoteControlCommandQueue] for production buffering.
 */
object RemoteControlCommandStore {

    private const val TAG = "RemoteControlStore"
    private const val PREFS = "remote_control_command_store"
    private const val KEY_ENTRIES = "entries"

    const val MAX_SIZE = 64
    const val EXPIRY_MS = 120_000L

    data class StoredEntry(
        val commandId: String,
        val commandType: String,
        val payload: JSONObject?,
        val enqueuedAtMs: Long,
        val foregroundPackage: String?,
        val state: RemoteControlCommandDurabilityState = RemoteControlCommandDurabilityState.QUEUED,
        val deadLetterReason: String? = null,
    ) {
        fun toQueueEntry(): RemoteControlCommandQueue.Entry = RemoteControlCommandQueue.Entry(
            commandId = commandId,
            commandType = commandType,
            payload = payload,
            enqueuedAtMs = enqueuedAtMs,
            foregroundPackage = foregroundPackage,
        )
    }

    fun enqueue(context: Context, entry: StoredEntry): Boolean {
        val entries = load(context)
        if (entries.any { it.commandId == entry.commandId && it.state == RemoteControlCommandDurabilityState.QUEUED }) {
            return false
        }
        val queued = entries.filter { it.state == RemoteControlCommandDurabilityState.QUEUED }
        if (queued.size >= MAX_SIZE) {
            val evicted = queued.minByOrNull { it.enqueuedAtMs }
            if (evicted != null) {
                deadLetter(context, evicted.commandId, "queue_overflow")
            }
        }
        val updated = load(context).toMutableList()
        updated.add(entry)
        save(context, updated)
        CommandDurabilityTelemetry.recordEnqueued(context, entry.commandId, updated.size)
        Log.i(TAG, "enqueued id=${entry.commandId} type=${entry.commandType} depth=${updated.size}")
        return true
    }

    fun drainQueued(context: Context): List<StoredEntry> {
        val all = load(context)
        val queued = all.filter { it.state == RemoteControlCommandDurabilityState.QUEUED }
        val remaining = all.filter { it.state != RemoteControlCommandDurabilityState.QUEUED }
        save(context, remaining)
        if (queued.isNotEmpty()) {
            CommandDurabilityTelemetry.recordDrained(context, queued.size)
        }
        return queued
    }

    fun expireStale(context: Context, onExpired: (StoredEntry) -> Unit) {
        val now = System.currentTimeMillis()
        val all = load(context)
        val (expired, kept) = all.partition { entry ->
            entry.state == RemoteControlCommandDurabilityState.QUEUED &&
                now - entry.enqueuedAtMs >= EXPIRY_MS
        }
        if (expired.isEmpty()) return
        save(context, kept)
        expired.forEach { entry ->
            deadLetter(context, entry.commandId, "accessibility_reconnect_timeout")
            onExpired(entry)
        }
    }

    fun deadLetter(context: Context, commandId: String, reason: String) {
        val all = load(context).toMutableList()
        val idx = all.indexOfFirst { it.commandId == commandId }
        if (idx < 0) return
        val entry = all[idx]
        all[idx] = entry.copy(
            state = RemoteControlCommandDurabilityState.DEAD_LETTER,
            deadLetterReason = reason,
        )
        save(context, all)
        CommandDurabilityTelemetry.recordDeadLetter(context, commandId, reason)
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.COMMAND_DURABILITY,
            "dead letter",
            "id=$commandId reason=$reason",
        )
        Log.w(TAG, "dead letter id=$commandId reason=$reason")
    }

    fun remove(context: Context, commandId: String) {
        val updated = load(context).filter { it.commandId != commandId }
        save(context, updated)
    }

    fun queuedSize(context: Context): Int =
        load(context).count { it.state == RemoteControlCommandDurabilityState.QUEUED }

    fun deadLetterEntries(context: Context): List<StoredEntry> =
        load(context).filter { it.state == RemoteControlCommandDurabilityState.DEAD_LETTER }

    fun clearDeadLetters(context: Context) {
        val kept = load(context).filter { it.state != RemoteControlCommandDurabilityState.DEAD_LETTER }
        save(context, kept)
    }

    /** @visibleForTesting */
    internal fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun load(context: Context): List<StoredEntry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    parseEntry(arr.getJSONObject(i))?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "corrupt store — resetting", e)
            emptyList()
        }
    }

    private fun save(context: Context, entries: List<StoredEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(serialize(it)) }
        prefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    private fun serialize(entry: StoredEntry): JSONObject = JSONObject()
        .put("commandId", entry.commandId)
        .put("commandType", entry.commandType)
        .put("enqueuedAtMs", entry.enqueuedAtMs)
        .put("foregroundPackage", entry.foregroundPackage ?: "")
        .put("state", entry.state.name)
        .put("deadLetterReason", entry.deadLetterReason ?: "")
        .put("payload", entry.payload?.toString() ?: "")

    private fun parseEntry(obj: JSONObject): StoredEntry? {
        val id = obj.optString("commandId")
        val type = obj.optString("commandType")
        if (id.isBlank() || type.isBlank()) return null
        val payloadRaw = obj.optString("payload")
        val payload = if (payloadRaw.isBlank()) null else try {
            JSONObject(payloadRaw)
        } catch (_: Exception) {
            null
        }
        return StoredEntry(
            commandId = id,
            commandType = type,
            payload = payload,
            enqueuedAtMs = obj.optLong("enqueuedAtMs", System.currentTimeMillis()),
            foregroundPackage = obj.optString("foregroundPackage").takeIf { it.isNotBlank() },
            state = RemoteControlCommandDurabilityState.fromPersisted(obj.optString("state")),
            deadLetterReason = obj.optString("deadLetterReason").takeIf { it.isNotBlank() },
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
