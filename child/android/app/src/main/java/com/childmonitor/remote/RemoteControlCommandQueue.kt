package com.childmonitor.remote

import org.json.JSONObject
import java.util.ArrayDeque

/**
 * In-memory queue for remote-control commands — used in unit tests.
 * Production buffering uses [RemoteControlCommandStore] (disk-backed).
 */
class RemoteControlCommandQueue(
    private val maxSize: Int = MAX_SIZE,
    private val expiryMs: Long = EXPIRY_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    data class Entry(
        val commandId: String,
        val commandType: String,
        val payload: JSONObject?,
        val enqueuedAtMs: Long,
        val foregroundPackage: String?,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", commandId)
            .put("commandType", commandType)
            .put("payload", payload)
    }

    private val queue = ArrayDeque<Entry>()

    @Synchronized
    fun enqueue(entry: Entry): Boolean {
        if (queue.any { it.commandId == entry.commandId }) {
            return false
        }
        while (queue.size >= maxSize) {
            queue.removeFirst()
        }
        queue.addLast(entry)
        return true
    }

    @Synchronized
    fun drain(): List<Entry> {
        val snapshot = queue.toList()
        queue.clear()
        return snapshot
    }

    @Synchronized
    fun expireStale(onExpired: (Entry) -> Unit) {
        val now = nowMs()
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.enqueuedAtMs >= expiryMs) {
                iterator.remove()
                onExpired(entry)
            }
        }
    }

    @Synchronized
    fun size(): Int = queue.size

    companion object {
        const val MAX_SIZE = 16
        const val EXPIRY_MS = 30_000L
    }
}
