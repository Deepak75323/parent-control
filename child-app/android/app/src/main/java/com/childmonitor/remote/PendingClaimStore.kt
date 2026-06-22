package com.childmonitor.remote

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Persists commands that were claimed from the server but not yet finalized —
 * enables replay after process death between claim and execute.
 */
object PendingClaimStore {

    private const val TAG = "PendingClaimStore"
    private const val PREFS = "pending_command_claims"
    private const val KEY_ENTRIES = "entries"

    data class ClaimedCommand(
        val commandId: String,
        val commandType: String,
        val payload: JSONObject?,
        val claimedAtMs: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", commandId)
            .put("commandType", commandType)
            .put("payload", payload)
    }

    fun save(context: Context, commandId: String, commandType: String, payload: JSONObject?) {
        val entries = load(context).toMutableList()
        entries.removeAll { it.commandId == commandId }
        entries.add(
            ClaimedCommand(
                commandId = commandId,
                commandType = commandType,
                payload = payload,
                claimedAtMs = System.currentTimeMillis(),
            ),
        )
        persist(context, entries)
        Log.i(TAG, "saved in-flight claim id=$commandId type=$commandType")
    }

    fun remove(context: Context, commandId: String) {
        val updated = load(context).filter { it.commandId != commandId }
        persist(context, updated)
    }

    fun all(context: Context): List<ClaimedCommand> = load(context)

    /** @visibleForTesting */
    internal fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun load(context: Context): List<ClaimedCommand> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("commandId")
                    val type = obj.optString("commandType")
                    if (id.isBlank() || type.isBlank()) continue
                    val payloadRaw = obj.optString("payload")
                    val payload = if (payloadRaw.isBlank()) null else try {
                        JSONObject(payloadRaw)
                    } catch (_: Exception) {
                        null
                    }
                    add(
                        ClaimedCommand(
                            commandId = id,
                            commandType = type,
                            payload = payload,
                            claimedAtMs = obj.optLong("claimedAtMs", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "corrupt claim store — resetting", e)
            emptyList()
        }
    }

    private fun persist(context: Context, entries: List<ClaimedCommand>) {
        val arr = org.json.JSONArray()
        entries.forEach { claim ->
            arr.put(
                JSONObject()
                    .put("commandId", claim.commandId)
                    .put("commandType", claim.commandType)
                    .put("claimedAtMs", claim.claimedAtMs)
                    .put("payload", claim.payload?.toString() ?: ""),
            )
        }
        prefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
