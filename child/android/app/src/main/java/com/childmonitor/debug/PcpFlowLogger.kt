package com.childmonitor.debug

import android.os.Looper
import android.util.Log
import com.childmonitor.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Structured native flow logger — filter Logcat with tag PCP_FLOW or [TRACE_ID=
 */
object PcpFlowLogger {

    private const val TAG = "PCP_FLOW"
    private val step = AtomicInteger(0)
    @Volatile
    var correlationId: String? = null

    @Volatile
    private var traceIdField: String? = null

    val traceId: String? get() = traceIdField

    @Volatile
    var currentState: String = StreamState.IDLE.name

    @Volatile
    private var previousStreamState: StreamState = StreamState.IDLE

    fun transitionState(next: StreamState, fn: String, detail: String? = null) {
        val from = previousStreamState
        previousStreamState = next
        currentState = next.name
        if (!BuildConfig.DEBUG) return
        val extra = detail?.let { " $it" } ?: ""
        Log.i(TAG, "${formatPrefix(Category.STREAM_STATE, fn)} STREAM_STATE ${from.name} → ${next.name}$extra")
    }

    fun resetStreamState() {
        previousStreamState = StreamState.IDLE
        currentState = StreamState.IDLE.name
    }

    fun setSession(sessionId: String?) {
        correlationId = sessionId
    }

    fun setTraceId(id: String?) {
        traceIdField = id
        if (id != null) correlationId = id
    }

    fun setState(state: String) {
        currentState = state
    }

    fun newSession(prefix: String = "sess"): String {
        val id = "${prefix}_${System.currentTimeMillis().toString(16)}"
        correlationId = id
        return id
    }

    fun newTrace(prefix: String = "child"): String {
        val id = "${prefix}_${java.util.UUID.randomUUID()}"
        setTraceId(id)
        return id
    }

    enum class Category {
        SCREEN_SHARE_FLOW,
        WEBRTC_FLOW,
        AUDIO_FLOW,
        PERMISSION_FLOW,
        MEDIA_PROJECTION,
        SOCKET_SIGNALING,
        SERVICE_STATE,
        BOOT_RECOVERY,
        COMMAND_DURABILITY,
        FCM,
        NOTIFICATION,
        TURN,
        CAMERA,
        STREAM_STATE,
    }

    private fun threadName(): String = Thread.currentThread().name

    private fun formatPrefix(category: Category, fn: String): String {
        val n = step.incrementAndGet()
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
        val trace = traceId?.let { "[TRACE_ID=$it]" } ?: "[TRACE_ID=none]"
        return "$trace [Child] [$currentState] [$fn] [${category.name}] #${n.toString().padStart(4, '0')} $ts thread=${threadName()}"
    }

    fun log(category: Category, fn: String, message: String, detail: String? = null) {
        if (!BuildConfig.DEBUG) return
        val extra = detail?.let { " $it" } ?: ""
        Log.i(TAG, "${formatPrefix(category, fn)} $message$extra")
    }

    fun log(category: Category, message: String, detail: String? = null) {
        log(category, "unknown", message, detail)
    }

    fun warn(category: Category, fn: String, message: String, detail: String? = null) {
        if (!BuildConfig.DEBUG) return
        val extra = detail?.let { " $it" } ?: ""
        Log.w(TAG, "${formatPrefix(category, fn)} WARN $message$extra")
    }

    fun warn(category: Category, message: String, detail: String? = null) {
        warn(category, "unknown", message, detail)
    }

    fun error(category: Category, fn: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        val line = "${formatPrefix(category, fn)} ERROR $message"
        if (throwable != null) Log.e(TAG, line, throwable) else Log.e(TAG, line)
    }

    fun error(category: Category, message: String, throwable: Throwable? = null) {
        error(category, "unknown", message, throwable)
    }

    fun failureReport(
        category: Category,
        fn: String,
        input: String? = null,
        output: String? = null,
        expected: String? = null,
        actual: String? = null,
        reason: String? = null,
        nextCauses: List<String>? = null,
        throwable: Throwable? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        val divider = "=".repeat(50)
        Log.e(TAG, divider)
        Log.e(TAG, "FAILURE REPORT")
        Log.e(TAG, divider)
        Log.e(TAG, "TRACE_ID: ${traceId ?: "none"}")
        Log.e(TAG, "Component: Child")
        Log.e(TAG, "Function: $fn")
        Log.e(TAG, "Category: ${category.name}")
        Log.e(TAG, "State: $currentState")
        Log.e(TAG, "Thread: ${threadName()}")
        Log.e(TAG, "MainThread: ${Looper.myLooper() == Looper.getMainLooper()}")
        input?.let { Log.e(TAG, "Input: $it") }
        output?.let { Log.e(TAG, "Output: $it") }
        expected?.let { Log.e(TAG, "Expected: $it") }
        actual?.let { Log.e(TAG, "Actual: $it") }
        reason?.let { Log.e(TAG, "Reason: $it") }
        nextCauses?.forEach { Log.e(TAG, "Next cause: $it") }
        if (throwable != null) Log.e(TAG, "Stacktrace:", throwable)
        Log.e(TAG, divider)
    }

    fun applyTraceFromPayload(data: Map<String, String>) {
        data["traceId"]?.takeIf { it.isNotBlank() }?.let { setTraceId(it) }
    }
}
