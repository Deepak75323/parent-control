package com.childmonitor.sync

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.childmonitor.deviceowner.DeviceOwnerManager
import com.childmonitor.remote.PendingClaimStore
import com.childmonitor.remote.RemoteControlCommandBuffer
import com.childmonitor.remote.RemoteControlCommandQueue
import com.childmonitor.remote.RemoteControlCommandStore
import com.childmonitor.remote.RemoteControlExecutor
import org.json.JSONObject

object DeviceCommandProcessor {

    private const val TAG = "DeviceCommandProcessor"

    private val remoteControlTypes = setOf(
        "TAP", "SWIPE", "GLOBAL_BACK", "GLOBAL_HOME", "TYPE_TEXT", "LAUNCH_APP",
    )

    fun pollAndExecute(context: Context) {
        pollAndExecuteWithMetrics(context)
    }

    data class PollMetrics(val pending: Int, val executed: Int)

    /** Poll pending commands and return counts for boot recovery telemetry. */
    fun pollAndExecuteWithMetrics(context: Context): PollMetrics {
        CommandAckRetry.flushPendingFinalizations(context)
        val json = DeviceHttpClient.get(context, "/api/v1/device/commands/pending") ?: return PollMetrics(0, 0)
        var executed = 0
        try {
            val root = JSONObject(json)
            val data = root.optJSONArray("data") ?: return PollMetrics(0, 0)
            val pending = data.length()
            for (i in 0 until data.length()) {
                val cmd = data.getJSONObject(i)
                val before = CommandExecutionStore.hasExecuted(context, cmd.optString("id"))
                execute(context, cmd)
                if (!before) executed++
            }
            return PollMetrics(pending, executed)
        } catch (e: Exception) {
            Log.e(TAG, "poll failed", e)
            return PollMetrics(0, executed)
        }
    }

    /**
     * Real-time push from STOMP /topic/device/{id}/commands — fetch payload from pending list.
     *
     * P1-3: For commands that carry a payload (TAP/SWIPE/TYPE_TEXT/LAUNCH_APP), a transient
     * network blip can cause the pending-list fetch to return null even though the command
     * exists. Previously we silently refused the command and waited for the next 3–15s poll
     * cycle. We now retry the fetch a few times with backoff so legitimate transient errors
     * don't add latency to user-visible remote control actions.
     */
    fun handleRealtimeCommand(context: Context, commandId: String, commandType: String) {
        if (commandId.isBlank() || commandType.isBlank()) return
        Log.i(TAG, "realtime command type=$commandType id=$commandId")
        CommandAckRetry.flushPendingFinalizations(context)

        val needsPayload = requiresPayload(commandType)
        val maxAttempts = if (needsPayload) 3 else 1
        var attempt = 0
        var pending: JSONObject? = null
        while (attempt < maxAttempts && pending == null) {
            if (attempt > 0) {
                val delayMs = 250L * (1L shl (attempt - 1)) // 250, 500ms
                try { Thread.sleep(delayMs) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                Log.i(TAG, "realtime fetch retry attempt=${attempt + 1} id=$commandId")
            }
            pending = fetchPendingCommand(context, commandId)
            attempt++
        }

        if (pending != null) {
            execute(context, pending)
            return
        }
        if (CommandExecutionStore.hasExecuted(context, commandId)) {
            val record = CommandExecutionStore.get(context, commandId)
            if (record != null) {
                Log.i(TAG, "realtime finalize-only for already-executed command id=$commandId")
                CommandAckRetry.finalizeCommand(context, commandId, record.success, record.error)
            }
            return
        }
        if (needsPayload) {
            Log.w(
                TAG,
                "realtime command missing payload after $maxAttempts attempt(s) — falling back to next poll id=$commandId type=$commandType",
            )
            return
        }
        execute(
            context,
            JSONObject()
                .put("id", commandId)
                .put("commandType", commandType),
        )
    }

    /**
     * Called from [com.childmonitor.AppBlockingAccessibilityService.onServiceConnected] after
     * the service instance is live — expires stale buffered commands and executes the rest.
     */
    fun flushBufferedRemoteCommands(context: Context) {
        CommandAckRetry.flushPendingFinalizations(context)
        RemoteControlCommandBuffer.expireStale(context) { expired ->
            if (DeviceHttpClient.claimCommand(context, expired.commandId)) {
                CommandAckRetry.finalizeCommand(
                    context,
                    expired.commandId,
                    false,
                    expired.deadLetterReason ?: "Accessibility reconnect timeout",
                )
            }
            RemoteControlCommandStore.remove(context, expired.commandId)
        }

        val buffered = RemoteControlCommandBuffer.drain(context)
        if (buffered.isEmpty()) return
        Log.i(TAG, "flushing ${buffered.size} buffered remote command(s) from disk")
        for (stored in buffered) {
            executeBufferedRemoteCommand(context, stored)
        }
    }

    /**
     * Replay a command that was claimed before process death — skips server claim.
     */
    fun replayClaimedCommand(context: Context, claim: PendingClaimStore.ClaimedCommand) {
        val cmd = claim.toJson()
        val type = claim.commandType
        if (type in remoteControlTypes) {
            executeRemoteControlCommand(
                context,
                cmd,
                fromBuffer = true,
                skipClaim = true,
                payloadOverride = claim.payload,
            )
        } else {
            executeNonRemoteCommand(context, cmd, skipClaim = true)
        }
    }

    private fun requiresPayload(commandType: String): Boolean =
        commandType in setOf("TAP", "SWIPE", "TYPE_TEXT", "LAUNCH_APP")

    private fun fetchPendingCommand(context: Context, commandId: String): JSONObject? {
        val json = DeviceHttpClient.get(context, "/api/v1/device/commands/pending") ?: return null
        return try {
            val root = JSONObject(json)
            val data = root.optJSONArray("data") ?: return null
            for (i in 0 until data.length()) {
                val cmd = data.getJSONObject(i)
                if (cmd.optString("id") == commandId) return cmd
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "fetch pending command failed", e)
            null
        }
    }

    private fun execute(context: Context, cmd: JSONObject) {
        val type = cmd.optString("commandType")
        if (type in remoteControlTypes) {
            executeRemoteControlCommand(context, cmd, fromBuffer = false)
            return
        }
        executeNonRemoteCommand(context, cmd)
    }

    private fun executeRemoteControlCommand(
        context: Context,
        cmd: JSONObject,
        fromBuffer: Boolean,
        skipClaim: Boolean = false,
        payloadOverride: JSONObject? = null,
    ) {
        val id = cmd.optString("id")
        val type = cmd.optString("commandType")
        if (id.isBlank()) return

        if (CommandExecutionStore.hasExecuted(context, id)) {
            val record = CommandExecutionStore.get(context, id)
            if (record != null) {
                Log.i(TAG, "skipping duplicate execution — finalize only id=$id type=$type")
                if (!skipClaim) DeviceHttpClient.claimCommand(context, id)
                CommandAckRetry.finalizeCommand(context, id, record.success, record.error)
            }
            PendingClaimStore.remove(context, id)
            return
        }

        if (!fromBuffer && !skipClaim) {
            when (RemoteControlCommandBuffer.tryBuffer(context, cmd)) {
                RemoteControlCommandBuffer.BufferResult.BUFFERED -> return
                RemoteControlCommandBuffer.BufferResult.REJECTED_DISABLED -> {
                    if (DeviceHttpClient.claimCommand(context, id)) {
                        CommandAckRetry.finalizeCommand(
                            context,
                            id,
                            false,
                            RemoteControlExecutor.unavailableReason(),
                        )
                    }
                    return
                }
                RemoteControlCommandBuffer.BufferResult.EXECUTE_NOW -> Unit
            }
        }

        if (!skipClaim) {
            if (!DeviceHttpClient.claimCommand(context, id)) {
                Log.i(TAG, "command already claimed or finished id=$id")
                return
            }
            PendingClaimStore.save(
                context,
                id,
                type,
                payloadOverride ?: parsePayload(cmd.opt("payload")),
            )
        }

        val payload = payloadOverride ?: parsePayload(cmd.opt("payload"))
        var success = false
        var error: String? = null
        try {
            val resolved = resolveRemotePayload(context, payload)
            success = RemoteControlExecutor.execute(resolved, type)
            if (!success) {
                error = RemoteControlExecutor.unavailableReason()
                    ?: "Remote control command failed: $type"
            }
        } catch (e: Exception) {
            error = e.message
        }
        PendingClaimStore.remove(context, id)
        CommandAckRetry.finalizeCommand(context, id, success, error)
    }

    private fun executeBufferedRemoteCommand(
        context: Context,
        stored: RemoteControlCommandStore.StoredEntry,
    ) {
        val entry = stored.toQueueEntry()
        if (RemoteControlCommandBuffer.isStaleScreen(entry)) {
            if (DeviceHttpClient.claimCommand(context, stored.commandId)) {
                CommandAckRetry.finalizeCommand(
                    context,
                    stored.commandId,
                    false,
                    "Screen changed while accessibility was reconnecting — command skipped",
                )
            }
            return
        }
        val cmd = entry.toJson().apply {
            if (entry.foregroundPackage != null) {
                put("foregroundPackage", entry.foregroundPackage)
            }
        }
        executeRemoteControlCommand(
            context,
            cmd,
            fromBuffer = true,
            payloadOverride = stored.payload,
        )
    }

    private fun executeNonRemoteCommand(
        context: Context,
        cmd: JSONObject,
        skipClaim: Boolean = false,
    ) {
        val id = cmd.optString("id")
        val type = cmd.optString("commandType")
        if (id.isBlank()) return

        if (CommandExecutionStore.hasExecuted(context, id)) {
            val record = CommandExecutionStore.get(context, id)
            if (record != null) {
                Log.i(TAG, "skipping duplicate non-remote execution — finalize only id=$id type=$type")
                DeviceHttpClient.claimCommand(context, id)
                CommandAckRetry.finalizeCommand(context, id, record.success, record.error)
            }
            return
        }

        if (!skipClaim && !DeviceHttpClient.claimCommand(context, id)) {
            Log.i(TAG, "command already claimed or finished id=$id")
            return
        }

        if (!skipClaim) {
            PendingClaimStore.save(context, id, type, parsePayload(cmd.opt("payload")))
        }

        val payload = parsePayload(cmd.opt("payload"))
        var success = false
        var error: String? = null
        try {
            success = when (type) {
                "ENABLE_KIOSK" -> DeviceOwnerManager.enableKiosk(context)
                "DISABLE_KIOSK" -> DeviceOwnerManager.disableKiosk(context)
                "REVOKE_MONITORING" -> {
                    com.childmonitor.monitoring.MonitoringRevokeManager.revokeMonitoringAndStop(
                        context,
                        source = "device_command",
                    )
                    true
                }
                "SYNC_INSTALLED_APPS" -> {
                    com.childmonitor.NativeSyncUploader.forceSyncInstalledApps(context)
                }
                else -> {
                    error = "Unknown command: $type"
                    false
                }
            }
            if (!success && error == null) {
                error = "Command failed: $type"
            }
        } catch (e: Exception) {
            error = e.message
        }
        PendingClaimStore.remove(context, id)
        CommandAckRetry.finalizeCommand(context, id, success, error)
    }

    private fun parsePayload(raw: Any?): JSONObject? {
        if (raw == null || raw == JSONObject.NULL) return null
        return when (raw) {
            is JSONObject -> raw
            is String -> if (raw.isBlank()) null else JSONObject(raw)
            else -> null
        }
    }

    /** Converts normalized viewport coords (0–1) to device pixels when present. */
    private fun resolveRemotePayload(context: Context, payload: JSONObject?): JSONObject? {
        if (payload == null) return null
        if (!payload.has("xNorm") && !payload.has("x1Norm")) return payload
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        display.getRealMetrics(metrics)
        val w = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val h = metrics.heightPixels.toFloat().coerceAtLeast(1f)
        val out = JSONObject(payload.toString())
        if (payload.has("xNorm")) {
            out.put("x", (payload.optDouble("xNorm", -1.0) * w).toInt())
            out.put("y", (payload.optDouble("yNorm", -1.0) * h).toInt())
        }
        if (payload.has("x1Norm")) {
            out.put("x1", (payload.optDouble("x1Norm", -1.0) * w).toInt())
            out.put("y1", (payload.optDouble("y1Norm", -1.0) * h).toInt())
            out.put("x2", (payload.optDouble("x2Norm", -1.0) * w).toInt())
            out.put("y2", (payload.optDouble("y2Norm", -1.0) * h).toInt())
        }
        return out
    }
}
