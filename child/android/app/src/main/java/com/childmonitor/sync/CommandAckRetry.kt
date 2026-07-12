package com.childmonitor.sync

import android.content.Context
import android.util.Log
import org.json.JSONObject
import com.childmonitor.constants.ApiPaths

/**
 * Retries execution fingerprint recording and ACK with exponential backoff so transient
 * network failures do not cause duplicate command execution after stale reclaim.
 */
object CommandAckRetry {

    private const val TAG = "CommandAckRetry"
    private const val MAX_INLINE_ATTEMPTS = 5
    private const val INITIAL_DELAY_MS = 1_000L
    private const val MAX_DELAY_MS = 16_000L

    fun finalizeCommand(context: Context, commandId: String, success: Boolean, error: String?) {
        CommandExecutionStore.markExecuted(context, commandId, success, error)

        val serverRecorded = recordExecutionWithRetry(context, commandId, success, error)
        if (serverRecorded) {
            CommandExecutionStore.markServerRecorded(context, commandId)
        }

        val acked = ackWithRetry(context, commandId, success, error)
        if (acked) {
            CommandExecutionStore.markAcked(context, commandId)
            CommandExecutionStore.remove(context, commandId)
            Log.i(
                TAG,
                "command finalized commandId=$commandId success=$success serverRecorded=$serverRecorded",
            )
        } else {
            Log.w(
                TAG,
                "command finalize incomplete commandId=$commandId success=$success " +
                    "serverRecorded=$serverRecorded — will retry on next poll",
            )
        }
    }

    fun flushPendingFinalizations(context: Context) {
        for (record in CommandExecutionStore.pendingFinalizations(context)) {
            Log.i(TAG, "retrying pending finalization commandId=${record.commandId}")
            if (!record.serverRecorded) {
                val recorded = recordExecutionWithRetry(
                    context,
                    record.commandId,
                    record.success,
                    record.error,
                )
                if (recorded) {
                    CommandExecutionStore.markServerRecorded(context, record.commandId)
                }
            }
            if (!record.acked) {
                val acked = ackWithRetry(context, record.commandId, record.success, record.error)
                if (acked) {
                    CommandExecutionStore.markAcked(context, record.commandId)
                    CommandExecutionStore.remove(context, record.commandId)
                }
            }
        }
    }

    fun recordExecutionWithRetry(
        context: Context,
        commandId: String,
        success: Boolean,
        error: String?,
    ): Boolean = retryOperation("recordExecution", commandId) {
        DeviceHttpClient.recordCommandExecution(context, commandId, success, error)
    }

    fun ackWithRetry(context: Context, commandId: String, success: Boolean, error: String?): Boolean =
        retryOperation("ack", commandId) {
            val body = JSONObject().put("success", success)
            if (error != null) body.put("errorMessage", error)
            DeviceHttpClient.postJson(context, ApiPaths.commandAck(commandId), body.toString())
        }

    private fun retryOperation(
        operation: String,
        commandId: String,
        block: () -> Boolean,
    ): Boolean {
        var delayMs = INITIAL_DELAY_MS
        repeat(MAX_INLINE_ATTEMPTS) { attempt ->
            if (block()) {
                if (attempt > 0) {
                    Log.i(TAG, "$operation succeeded on retry attempt=${attempt + 1} commandId=$commandId")
                }
                return true
            }
            val nextAttempt = attempt + 1
            if (nextAttempt >= MAX_INLINE_ATTEMPTS) {
                Log.w(TAG, "$operation exhausted inline retries commandId=$commandId")
                return false
            }
            Log.w(
                TAG,
                "$operation failed attempt=$nextAttempt commandId=$commandId retryingInMs=$delayMs",
            )
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
            delayMs = (delayMs * 2).coerceAtMost(MAX_DELAY_MS)
        }
        return false
    }
}
