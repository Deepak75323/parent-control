package com.childmonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StoredNotification(
    val localId: String,
    val packageName: String?,
    val title: String?,
    val body: String?,
    val postedAtMs: Long,
    var synced: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("localId", localId)
        .put("packageName", packageName)
        .put("title", title)
        .put("body", body)
        .put("postedAt", postedAtMs)
        .put("synced", synced)

    companion object {
        fun fromJson(obj: JSONObject) = StoredNotification(
            localId = obj.getString("localId"),
            packageName = obj.optString("packageName").ifBlank { null },
            title = obj.optString("title").ifBlank { null },
            body = obj.optString("body").ifBlank { null },
            postedAtMs = obj.getLong("postedAt"),
            synced = obj.optBoolean("synced", false),
        )
    }
}

object NotificationStore {
    private const val PREFS = "notification_store"
    private const val KEY_ITEMS = "items"

    fun add(
        context: Context,
        packageName: String?,
        title: String?,
        body: String?,
        postedAtMs: Long,
    ) {
        val items = loadAll(context).toMutableList()
        items.add(
            0,
            StoredNotification(
                localId = UUID.randomUUID().toString(),
                packageName = packageName,
                title = title,
                body = body,
                postedAtMs = postedAtMs,
            ),
        )
        saveAll(context, items.take(500))
    }

    fun getPending(context: Context): List<StoredNotification> =
        loadAll(context).filter { !it.synced }

    fun markSynced(context: Context, localIds: List<String>) {
        if (localIds.isEmpty()) return
        val idSet = localIds.toSet()
        val updated = loadAll(context).map {
            if (idSet.contains(it.localId)) it.copy(synced = true) else it
        }
        saveAll(context, updated)
    }

    private fun loadAll(context: Context): List<StoredNotification> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                add(StoredNotification.fromJson(arr.getJSONObject(i)))
            }
        }
    }

    private fun saveAll(context: Context, items: List<StoredNotification>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }
}
