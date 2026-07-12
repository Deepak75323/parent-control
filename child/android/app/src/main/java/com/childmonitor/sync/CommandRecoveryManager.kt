package com.childmonitor.sync

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.remote.CommandDurabilityTelemetry
import com.childmonitor.remote.PendingClaimStore
import com.childmonitor.remote.RemoteControlCommandBuffer
import com.childmonitor.remote.RemoteControlCommandStore
import com.childmonitor.sync.CommandExecutionStore
import com.childmonitor.constants.ApiPaths

/**
 * Replays persisted commands after process death, reboot, or accessibility reconnect.
 */
object CommandRecoveryManager {

    private const val TAG = "CommandRecovery"

    /**
     * Called on process start and boot-recovery fast phase — replays in-flight claims
     * and processes dead letters; does not flush accessibility buffer (needs service).
     */
    fun onProcessStart(context: Context, trigger: String = "process_start") {
        if (!MonitoringPrefs.isPaired(context)) return
        val app = context.applicationContext
        Log.i(TAG, "onProcessStart trigger=$trigger")

        CommandAckRetry.flushPendingFinalizations(app)

        val replayed = replayPendingClaims(app) + reconcileInFlightFromServer(app)
        val deadLettered = processDeadLetters(app)

        CommandDurabilityTelemetry.recordRecovery(
            app,
            trigger,
            replayedClaims = replayed,
            flushedBuffer = deadLettered,
        )
    }

    /**
     * Called when AccessibilityService connects — flush disk-backed buffer.
     */
    fun onAccessibilityConnected(context: Context) {
        DeviceCommandProcessor.flushBufferedRemoteCommands(context)
    }

    private fun replayPendingClaims(context: Context): Int {
        val claims = PendingClaimStore.all(context)
        if (claims.isEmpty()) return 0
        Log.i(TAG, "replaying ${claims.size} in-flight claim(s)")
        var replayed = 0
        for (claim in claims) {
            if (CommandExecutionStore.hasExecuted(context, claim.commandId)) {
                PendingClaimStore.remove(context, claim.commandId)
                continue
            }
            CommandDurabilityTelemetry.recordReplay(context, claim.commandId, "pending_claim")
            DeviceCommandProcessor.replayClaimedCommand(context, claim)
            replayed++
        }
        return replayed
    }

    private fun reconcileInFlightFromServer(context: Context): Int {
        val json = DeviceHttpClient.get(context, ApiPaths.DEVICE_COMMANDS_IN_FLIGHT) ?: return 0
        var replayed = 0
        try {
            val root = org.json.JSONObject(json)
            val data = root.optJSONArray("data") ?: return 0
            for (i in 0 until data.length()) {
                val cmd = data.getJSONObject(i)
                val id = cmd.optString("id")
                if (id.isBlank() || CommandExecutionStore.hasExecuted(context, id)) continue
                val type = cmd.optString("commandType")
                val payload = cmd.optJSONObject("payload")
                    ?: cmd.optString("payload").takeIf { it.isNotBlank() }?.let {
                        try {
                            org.json.JSONObject(it)
                        } catch (_: Exception) {
                            null
                        }
                    }
                PendingClaimStore.save(context, id, type, payload)
                CommandDurabilityTelemetry.recordReplay(context, id, "server_in_flight")
                DeviceCommandProcessor.replayClaimedCommand(
                    context,
                    PendingClaimStore.ClaimedCommand(
                        commandId = id,
                        commandType = type,
                        payload = payload,
                        claimedAtMs = System.currentTimeMillis(),
                    ),
                )
                replayed++
            }
        } catch (e: Exception) {
            Log.e(TAG, "in-flight reconciliation failed", e)
        }
        return replayed
    }

    private fun processDeadLetters(context: Context): Int {
        val dead = RemoteControlCommandStore.deadLetterEntries(context)
        if (dead.isEmpty()) return 0
        var processed = 0
        for (entry in dead) {
            val reason = entry.deadLetterReason ?: "dead_letter"
            Thread {
                if (DeviceHttpClient.claimCommand(context, entry.commandId)) {
                    CommandAckRetry.finalizeCommand(context, entry.commandId, false, reason)
                }
                RemoteControlCommandStore.remove(context, entry.commandId)
            }.start()
            processed++
        }
        return processed
    }
}
