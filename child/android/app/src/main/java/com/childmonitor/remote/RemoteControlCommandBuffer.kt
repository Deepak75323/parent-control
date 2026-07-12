package com.childmonitor.remote

import android.content.Context
import android.util.Log
import com.childmonitor.AccessibilityHealthChecker
import com.childmonitor.AppBlockingAccessibilityService
import com.childmonitor.ForegroundAppTracker
import org.json.JSONObject

/**
 * Buffers remote-control commands when Accessibility is enabled in settings but the service
 * instance is temporarily disconnected. All queued commands are persisted to disk.
 */
object RemoteControlCommandBuffer {

    private const val TAG = "RemoteControlBuffer"

    enum class BufferResult {
        /** Service connected — execute immediately. */
        EXECUTE_NOW,
        /** Queued on disk until onServiceConnected flush. */
        BUFFERED,
        /** Accessibility disabled in system settings — reject immediately. */
        REJECTED_DISABLED,
    }

    private val screenSensitiveTypes = setOf("TAP", "SWIPE", "TYPE_TEXT")

    fun tryBuffer(context: Context, cmd: JSONObject): BufferResult {
        if (AppBlockingAccessibilityService.instance != null) {
            return BufferResult.EXECUTE_NOW
        }
        if (!AccessibilityHealthChecker.isAccessibilityEnabled(context)) {
            return BufferResult.REJECTED_DISABLED
        }
        val id = cmd.optString("id")
        val type = cmd.optString("commandType")
        if (id.isBlank() || type.isBlank()) {
            return BufferResult.REJECTED_DISABLED
        }
        val payload = parsePayload(cmd)
        val stored = RemoteControlCommandStore.StoredEntry(
            commandId = id,
            commandType = type,
            payload = payload,
            enqueuedAtMs = System.currentTimeMillis(),
            foregroundPackage = ForegroundAppTracker.current(),
        )
        val added = RemoteControlCommandStore.enqueue(context.applicationContext, stored)
        Log.i(
            TAG,
            "buffered remote command id=$id type=$type fg=${stored.foregroundPackage} " +
                "added=$added depth=${RemoteControlCommandStore.queuedSize(context)}",
        )
        return BufferResult.BUFFERED
    }

    fun isStaleScreen(entry: RemoteControlCommandQueue.Entry): Boolean {
        if (entry.commandType !in screenSensitiveTypes) {
            return false
        }
        val expected = entry.foregroundPackage ?: return false
        val current = ForegroundAppTracker.current() ?: return false
        return expected != current
    }

    fun expireStale(context: Context, onExpired: (RemoteControlCommandStore.StoredEntry) -> Unit) {
        RemoteControlCommandStore.expireStale(context, onExpired)
    }

    fun drain(context: Context): List<RemoteControlCommandStore.StoredEntry> =
        RemoteControlCommandStore.drainQueued(context)

    fun queueSize(context: Context): Int = RemoteControlCommandStore.queuedSize(context)

    private fun parsePayload(cmd: JSONObject): JSONObject? =
        cmd.optJSONObject("payload")
            ?: cmd.opt("payload")?.let { raw ->
                when (raw) {
                    is JSONObject -> raw
                    is String -> if (raw.isBlank()) null else JSONObject(raw)
                    else -> null
                }
            }

    /** @visibleForTesting */
    internal fun resetForTests(context: Context) {
        RemoteControlCommandStore.clear(context)
    }
}
