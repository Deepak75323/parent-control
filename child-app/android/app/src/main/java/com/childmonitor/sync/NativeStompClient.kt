package com.childmonitor.sync

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal STOMP 1.2 client over OkHttp WebSocket for device-side streaming events.
 */
class NativeStompClient(
    private val wsUrl: String,
    private val bearerToken: String,
    private val onMessage: (destination: String, body: String) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
) {
    private val tag = "NativeStompClient"
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connected = AtomicBoolean(false)
    private val subscriptions = mutableListOf<Pair<String, String>>()
    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0

    fun connect(subscriptions: List<Pair<String, String>>) {
        synchronized(this.subscriptions) {
            this.subscriptions.clear()
            this.subscriptions.addAll(subscriptions)
        }
        openSocket()
    }

    fun disconnect() {
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS
        connected.set(false)
        webSocket?.close(1000, "shutdown")
        webSocket = null
    }

    fun isConnected(): Boolean = connected.get()

    /** Subscribe to an additional destination (safe before or after CONNECTED). */
    fun addSubscription(id: String, destination: String) {
        synchronized(subscriptions) {
            subscriptions.removeAll { it.first == id }
            subscriptions.add(id to destination)
        }
        if (connected.get()) {
            sendFrame(
                webSocket,
                "SUBSCRIBE",
                mapOf("id" to id, "destination" to destination, "ack" to "auto"),
                "",
            )
            Log.i(tag, "Dynamic SUBSCRIBE $destination id=$id")
        }
    }

    fun removeSubscription(id: String) {
        synchronized(subscriptions) {
            subscriptions.removeAll { it.first == id }
        }
        if (connected.get()) {
            sendFrame(webSocket, "UNSUBSCRIBE", mapOf("id" to id), "")
            Log.i(tag, "UNSUBSCRIBE id=$id")
        }
    }

    private fun openSocket() {
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $bearerToken")
            .build()
        webSocket?.cancel()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "WebSocket open — sending STOMP CONNECT")
                sendFrame(
                    webSocket,
                    "CONNECT",
                    mapOf(
                        "accept-version" to "1.2,1.1",
                        "heart-beat" to "10000,10000",
                        "Authorization" to "Bearer $bearerToken",
                    ),
                    "",
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleStompFrame(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleStompFrame(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(tag, "WebSocket closed code=$code reason=$reason")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure", t)
                handleDisconnect()
            }
        })
    }

    private fun handleStompFrame(raw: String) {
        if (raw.isBlank()) return
        val nullIdx = raw.indexOf('\u0000')
        val frame = if (nullIdx >= 0) raw.substring(0, nullIdx) else raw
        val lines = frame.split('\n')
        if (lines.isEmpty()) return
        val command = lines[0].trim()
        val headers = mutableMapOf<String, String>()
        var bodyStart = 1
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) {
                bodyStart = i + 1
                break
            }
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon)] = line.substring(colon + 1)
            }
        }
        val body = if (bodyStart < lines.size) {
            lines.subList(bodyStart, lines.size).joinToString("\n")
        } else {
            ""
        }

        when (command) {
            "CONNECTED" -> {
                connected.set(true)
                reconnectAttempts = 0
                Log.i(tag, "STOMP CONNECTED")
                val subsCopy = synchronized(subscriptions) { subscriptions.toList() }
                subsCopy.forEachIndexed { index, (id, destination) ->
                    sendFrame(
                        webSocket,
                        "SUBSCRIBE",
                        mapOf(
                            "id" to id,
                            "destination" to destination,
                            "ack" to "auto",
                        ),
                        "",
                    )
                    Log.i(tag, "SUBSCRIBE $destination id=$id")
                }
                mainHandler.post { onConnected() }
            }
            "MESSAGE" -> {
                val destination = headers["destination"] ?: return
                onMessage(destination, body)
            }
            "ERROR" -> Log.e(tag, "STOMP ERROR: $body")
        }
    }

    private fun handleDisconnect() {
        val wasConnected = connected.getAndSet(false)
        if (wasConnected) {
            mainHandler.post { onDisconnected() }
        }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        val delay = RECONNECT_BASE_MS * reconnectAttempts.coerceAtMost(5)
        Log.i(tag, "Scheduling STOMP reconnect in ${delay}ms (attempt $reconnectAttempts)")
        mainHandler.postDelayed({ openSocket() }, delay)
    }

    private fun sendFrame(
        socket: WebSocket?,
        command: String,
        headers: Map<String, String>,
        body: String,
    ) {
        val sb = StringBuilder()
        sb.append(command).append('\n')
        headers.forEach { (k, v) -> sb.append(k).append(':').append(v).append('\n') }
        sb.append('\n')
        sb.append(body)
        sb.append('\u0000')
        socket?.send(sb.toString())
    }

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 100
        private const val RECONNECT_BASE_MS = 3000L

        fun wsNativeUrl(apiBaseUrl: String): String {
            val trimmed = apiBaseUrl.trimEnd('/')
            val wsBase = when {
                trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
                trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
                else -> "ws://$trimmed"
            }
            return "$wsBase/ws-native"
        }
    }
}
