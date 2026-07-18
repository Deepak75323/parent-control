package com.childmonitor.sync

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.capture.NativeStreamPublisher
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.native.ChildMonitorNativeModule
import com.childmonitor.telemetry.StompOwnershipTelemetry
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Single authoritative STOMP owner for the child device app.
 *
 * Native Android owns the WebSocket connection, all device-topic subscriptions,
 * dynamic signaling-room subscriptions, reconnect, and message deduplication.
 * React Native receives signaling via bridge events only — it must not open a
 * parallel STOMP connection.
 */
object NativeStompOwner {

    private const val TAG = "NativeStompOwner"
    private const val DEDUP_MAX = 256
    private const val DEDUP_TTL_MS = 30_000L

    enum class OwnershipState {
        STOPPED,
        CONNECTING,
        NATIVE_AUTHORITATIVE,
        RECONNECTING,
    }

    @Volatile
    private var stompClient: NativeStompClient? = null

  @Volatile
    private var appContext: Context? = null

    /** Last API base used for a successful/attempted STOMP connect — skip no-op reconnects. */
    @Volatile
    private var lastConnectedApiBase: String? = null

    private val state = AtomicReference(OwnershipState.STOPPED)
    private val signalingRooms = ConcurrentHashMap<String, String>()
    private val dedupSeenAt = object : LinkedHashMap<String, Long>(DEDUP_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > DEDUP_MAX
        }
    }

    @Synchronized
    fun start(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) {
            Log.i(TAG, "Not paired — skip STOMP owner")
            return
        }
        appContext = context.applicationContext
        connectLocked(context.applicationContext)
    }

    @Synchronized
    fun ensureConnected(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) return
        appContext = context.applicationContext
        if (stompClient?.isConnected() == true) {
            state.set(OwnershipState.NATIVE_AUTHORITATIVE)
            return
        }
        // Do not tear down a client that is already connecting/reconnecting —
        // recreating mid-backoff multiplies open sockets and can exhaust nginx.
        val current = state.get()
        if (stompClient != null &&
            (current == OwnershipState.CONNECTING || current == OwnershipState.RECONNECTING)
        ) {
            Log.d(TAG, "ensureConnected skipped — already $current")
            return
        }
        connectLocked(context.applicationContext)
    }

    @Synchronized
    fun stop() {
        state.set(OwnershipState.STOPPED)
        stompClient?.disconnect()
        stompClient = null
        lastConnectedApiBase = null
        signalingRooms.clear()
        synchronized(dedupSeenAt) { dedupSeenAt.clear() }
        appContext = null
        StompOwnershipTelemetry.recordState(OwnershipState.STOPPED.name, false)
    }

    @Synchronized
    fun onApiBaseUrlChanged(context: Context) {
        if (appContext == null && !MonitoringPrefs.isPaired(context)) return
        val next = MonitoringPrefs.apiBaseUrl(context)?.trimEnd('/')
        appContext = context.applicationContext
        // Dedup: JS used to push the same production URL on every axios request.
        if (next != null && next == lastConnectedApiBase && isConnected()) {
            Log.d(TAG, "API base unchanged ($next) — skip STOMP reconnect")
            return
        }
        Log.i(TAG, "API base changed — reconnecting authoritative STOMP")
        connectLocked(context.applicationContext)
    }

    fun isConnected(): Boolean = stompClient?.isConnected() == true

    fun ownershipState(): OwnershipState = state.get()

    fun subscribeSignalingRoom(roomId: String) {
        if (roomId.isBlank()) return
        val subId = "sig-$roomId"
        signalingRooms[roomId] = subId
        stompClient?.addSubscription(subId, "/topic/signaling/$roomId")
        StompOwnershipTelemetry.recordSignalingSubscribe(roomId)
        Log.i(TAG, "Signaling room subscribed roomId=$roomId")
    }

    fun unsubscribeSignalingRoom(roomId: String) {
        val subId = signalingRooms.remove(roomId) ?: return
        stompClient?.removeSubscription(subId)
        StompOwnershipTelemetry.recordSignalingUnsubscribe(roomId)
        Log.i(TAG, "Signaling room unsubscribed roomId=$roomId")
    }

    fun healthSnapshot(): Map<String, Any> {
        return mapOf(
            "owner" to "native",
            "state" to state.get().name,
            "connected" to isConnected(),
            "signalingRooms" to signalingRooms.keys.toList(),
            "dedupCacheSize" to synchronized(dedupSeenAt) { dedupSeenAt.size },
        )
    }

    @Synchronized
    private fun connectLocked(context: Context) {
        val token = MonitoringPrefs.deviceToken(context) ?: return
        val apiBase = NativeApiResolver.resolveWorkingBase(context)
            ?: MonitoringPrefs.apiBaseUrl(context)
            ?: return
        lastConnectedApiBase = apiBase.trimEnd('/')
        val deviceId = parseDeviceIdFromToken(token) ?: return
        val wsUrl = NativeStompClient.wsNativeUrl(apiBase)

        state.set(OwnershipState.CONNECTING)
        val deviceSubs = listOf(
            "sub-stream-request" to "/topic/device/$deviceId/stream-request",
            "sub-stream-stop" to "/topic/device/$deviceId/stream-stop",
            "sub-stream-control" to "/topic/device/$deviceId/stream-control",
            "sub-commands" to "/topic/device/$deviceId/commands",
            "sub-policy-updates" to "/topic/device/$deviceId/policy-updates",
        )
        val signalingSubs = signalingRooms.map { (roomId, subId) ->
            subId to "/topic/signaling/$roomId"
        }

        stompClient?.disconnect()
        stompClient = NativeStompClient(
            wsUrl = wsUrl,
            bearerToken = token,
            onMessage = { destination, body -> dispatchMessage(context, destination, body) },
            onConnected = {
                state.set(OwnershipState.NATIVE_AUTHORITATIVE)
                StompOwnershipTelemetry.recordState(OwnershipState.NATIVE_AUTHORITATIVE.name, true)
                PcpFlowLogger.log(PcpFlowLogger.Category.SOCKET_SIGNALING, "onConnected", "STOMP connected", "url=$wsUrl")
                Log.i(TAG, "Authoritative STOMP connected")
            },
            onDisconnected = {
                state.set(OwnershipState.RECONNECTING)
                StompOwnershipTelemetry.recordState(OwnershipState.RECONNECTING.name, false)
                PcpFlowLogger.warn(PcpFlowLogger.Category.SOCKET_SIGNALING, "onDisconnected", "STOMP disconnected — reconnect scheduled")
                Log.w(TAG, "Authoritative STOMP disconnected — reconnect scheduled")
            },
        ).also { it.connect(deviceSubs + signalingSubs) }
    }

    private fun dispatchMessage(context: Context, destination: String, body: String) {
        if (isDuplicate(destination, body)) {
            StompOwnershipTelemetry.recordDedup(destination)
            PcpFlowLogger.log(PcpFlowLogger.Category.SOCKET_SIGNALING, "dispatchMessage", "Deduplicated STOMP message", "destination=$destination")
            Log.d(TAG, "Deduplicated STOMP message destination=$destination")
            return
        }
        PcpFlowLogger.log(PcpFlowLogger.Category.SOCKET_SIGNALING, "dispatchMessage", "STOMP message dispatch", "destination=$destination bodyLen=${body.length}")
        when {
            destination.contains("/signaling/") -> {
                val roomId = destination.substringAfterLast('/')
                NativeStreamPublisher.onSignalingPush(roomId, body)
                ChildMonitorNativeModule.emitSignalingMessage(roomId, body)
            }
            else -> NativeStreamingCoordinator.handleStompMessageFromOwner(
                context.applicationContext,
                destination,
                body,
            )
        }
    }

    private fun isDuplicate(destination: String, body: String): Boolean {
        val key = dedupKey(destination, body)
        val now = System.currentTimeMillis()
        synchronized(dedupSeenAt) {
            val prev = dedupSeenAt[key]
            if (prev != null && now - prev < DEDUP_TTL_MS) {
                return true
            }
            dedupSeenAt[key] = now
            dedupSeenAt.entries.removeIf { now - it.value > DEDUP_TTL_MS }
        }
        return false
    }

    private fun dedupKey(destination: String, body: String): String {
        try {
            val json = JSONObject(body)
            json.optString("commandId").takeIf { it.isNotBlank() }?.let { return "cmd:$it" }
            json.optString("id").takeIf { it.isNotBlank() }?.let { return "sig:$it" }
            val sessionId = json.optString("sessionId")
            if (sessionId.isNotBlank()) {
                val roomId = json.optString("roomId", json.optString("signalingRoomId", ""))
                return "stream:$roomId:$sessionId:$destination"
            }
        } catch (_: Exception) {
        }
        return "$destination:${body.hashCode()}"
    }

    private fun parseDeviceIdFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            var payload = parts[1]
            val pad = (4 - payload.length % 4) % 4
            payload += "=".repeat(pad)
            val json = JSONObject(
                String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)),
            )
            json.optString("deviceId").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
