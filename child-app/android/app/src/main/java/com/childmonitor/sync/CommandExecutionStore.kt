package com.childmonitor.sync

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Local execution fingerprint store — survives process death and prevents duplicate
 * side effects when the server re-queues a command after a failed ACK.
 */
object CommandExecutionStore {

    private const val TAG = "CommandExecutionStore"
    private const val PREFS = "command_execution_fingerprints"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 64
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000

    data class Record(
        val commandId: String,
        val success: Boolean,
        val error: String?,
        val executedAtMs: Long,
        val serverRecorded: Boolean,
        val acked: Boolean,
    )

    fun hasExecuted(context: Context, commandId: String): Boolean =
        get(context, commandId) != null

    fun get(context: Context, commandId: String): Record? {
        val entries = loadEntries(context)
        val raw = entries.optJSONObject(commandId) ?: return null
        val executedAt = raw.optLong("executedAtMs", 0L)
        if (executedAt <= 0L || System.currentTimeMillis() - executedAt > TTL_MS) {
            remove(context, commandId)
            return null
        }
        return Record(
            commandId = commandId,
            success = raw.optBoolean("success", false),
            error = raw.optString("error").takeIf { it.isNotBlank() },
            executedAtMs = executedAt,
            serverRecorded = raw.optBoolean("serverRecorded", false),
            acked = raw.optBoolean("acked", false),
        )
    }

    fun markExecuted(context: Context, commandId: String, success: Boolean, error: String?) {
        val entries = loadEntries(context)
        entries.put(
            commandId,
            JSONObject()
                .put("success", success)
                .put("error", error ?: "")
                .put("executedAtMs", System.currentTimeMillis())
                .put("serverRecorded", false)
                .put("acked", false),
        )
        prune(entries)
        saveEntries(context, entries)
        Log.i(TAG, "marked executed locally commandId=$commandId success=$success")
    }

    fun markServerRecorded(context: Context, commandId: String) {
        update(context, commandId) { it.put("serverRecorded", true) }
        Log.i(TAG, "server execution fingerprint recorded commandId=$commandId")
    }

    fun markAcked(context: Context, commandId: String) {
        update(context, commandId) { it.put("acked", true) }
        Log.i(TAG, "ack confirmed commandId=$commandId")
    }

    fun remove(context: Context, commandId: String) {
        val entries = loadEntries(context)
        if (entries.remove(commandId) != null) {
            saveEntries(context, entries)
            Log.d(TAG, "removed fingerprint commandId=$commandId")
        }
    }

    fun pendingFinalizations(context: Context): List<Record> {
        purgeExpired(context)
        val entries = loadEntries(context)
        val pending = mutableListOf<Record>()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val commandId = keys.next()
            val record = get(context, commandId) ?: continue
            if (!record.serverRecorded || !record.acked) {
                pending.add(record)
            } else {
                remove(context, commandId)
            }
        }
        return pending
    }

    private fun update(context: Context, commandId: String, mutate: (JSONObject) -> Unit) {
        val entries = loadEntries(context)
        val raw = entries.optJSONObject(commandId) ?: return
        mutate(raw)
        entries.put(commandId, raw)
        saveEntries(context, entries)
    }

    private fun purgeExpired(context: Context) {
        val entries = loadEntries(context)
        val now = System.currentTimeMillis()
        val keys = entries.keys().asSequence().toList()
        var changed = false
        for (key in keys) {
            val executedAt = entries.optJSONObject(key)?.optLong("executedAtMs", 0L) ?: 0L
            if (executedAt <= 0L || now - executedAt > TTL_MS) {
                entries.remove(key)
                changed = true
            }
        }
        if (changed) saveEntries(context, entries)
    }

    private fun prune(entries: JSONObject) {
        val keys = entries.keys().asSequence().toList()
        if (keys.size <= MAX_ENTRIES) return
        val sorted = keys.sortedBy { entries.optJSONObject(it)?.optLong("executedAtMs", 0L) ?: 0L }
        val removeCount = keys.size - MAX_ENTRIES
        for (i in 0 until removeCount) {
            entries.remove(sorted[i])
        }
    }

    private fun loadEntries(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            Log.w(TAG, "corrupt fingerprint store — resetting", e)
            JSONObject()
        }
    }

    private fun saveEntries(context: Context, entries: JSONObject) {
        prefs(context).edit().putString(KEY_ENTRIES, entries.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
