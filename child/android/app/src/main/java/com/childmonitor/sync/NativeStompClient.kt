package com.childmonitor.sync

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.security.CertPinningConfig
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

    // Build the OkHttp client once per NativeStompClient instance.
    // In release builds, CertPinningConfig.buildPinner() attaches an explicit
    // CertificatePinner for defence-in-depth on the wss:// path — Android's
    // Network Security Config covers HttpURLConnection, but OkHttp runs its own
    // trust chain and requires a separate programmatic pinner.
    // In debug builds, buildPinner() returns null and no pinning is applied so
    // developers can use local servers or intercept proxies freely.
    private val client: OkHttpClient = run {
        val builder = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
        CertPinningConfig.buildPinner()?.let { pinner ->
            builder.certificatePinner(pinner)
            android.util.Log.i("NativeStompClient", "Certificate pinning active for ${pinner}")
        }
        builder.build()
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connected = AtomicBoolean(false)
    /** Once true, no further sockets or reconnects are allowed. */
    private val stopped = AtomicBoolean(false)
    /** Guards against OkHttp calling both onFailure and onClosed for one teardown. */
    private val reconnectScheduled = AtomicBoolean(false)
    private val subscriptions = mutableListOf<Pair<String, String>>()
    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null

    fun connect(subscriptions: List<Pair<String, String>>) {
        stopped.set(false)
        reconnectScheduled.set(false)
        cancelPendingReconnect()
        synchronized(this.subscriptions) {
            this.subscriptions.clear()
            this.subscriptions.addAll(subscriptions)
        }
        openSocket()
    }

    fun disconnect() {
        stopped.set(true)
        reconnectAttempts = StompReconnectPolicy.MAX_ATTEMPTS
        reconnectScheduled.set(false)
        cancelPendingReconnect()
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
        if (stopped.get()) return
        reconnectScheduled.set(false)
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $bearerToken")
            .build()
        webSocket?.cancel()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (stopped.get()) {
                    webSocket.close(1000, "stopped")
                    return
                }
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
                reconnectScheduled.set(false)
                Log.i(tag, "STOMP CONNECTED")
                val subsCopy = synchronized(subscriptions) { subscriptions.toList() }
                subsCopy.forEach { (id, destination) ->
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
        if (stopped.get()) return
        val wasConnected = connected.getAndSet(false)
        if (wasConnected) {
            mainHandler.post { onDisconnected() }
        }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (stopped.get()) return
        if (reconnectAttempts >= StompReconnectPolicy.MAX_ATTEMPTS) {
            Log.w(tag, "Giving up STOMP reconnect after $reconnectAttempts attempts")
            return
        }
        // onFailure + onClosed for the same teardown must not schedule twice.
        if (!reconnectScheduled.compareAndSet(false, true)) {
            Log.d(tag, "Reconnect already scheduled — skipping duplicate")
            return
        }
        reconnectAttempts++
        val delay = StompReconnectPolicy.delayMs(reconnectAttempts)
        Log.i(tag, "Scheduling STOMP reconnect in ${delay}ms (attempt $reconnectAttempts)")
        cancelPendingReconnect()
        val runnable = Runnable {
            reconnectRunnable = null
            if (!stopped.get()) {
                openSocket()
            }
        }
        reconnectRunnable = runnable
        mainHandler.postDelayed(runnable, delay)
    }

    private fun cancelPendingReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
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
