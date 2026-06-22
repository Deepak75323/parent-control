package com.childmonitor.debug

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Structured native flow logger — filter Logcat with tag PCP_FLOW or [PCP:
 */
object PcpFlowLogger {

    private const val TAG = "PCP_FLOW"
    private val step = AtomicInteger(0)
    @Volatile
    var correlationId: String? = null

    fun setSession(sessionId: String?) {
        correlationId = sessionId
    }

    fun newSession(prefix: String = "sess"): String {
        correlationId = "${prefix}_${System.currentTimeMillis().toString(16)}"
        return correlationId!!
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
    }

    fun log(category: Category, message: String, detail: String? = null) {
        val n = step.incrementAndGet()
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
        val corr = correlationId?.let { " corr=$it" } ?: ""
        val extra = detail?.let { " $it" } ?: ""
        val line = "[PCP:${category.name}] #${n.toString().padStart(4, '0')}$corr $ts $message$extra"
        Log.i(TAG, line)
    }

    fun warn(category: Category, message: String, detail: String? = null) {
        val n = step.incrementAndGet()
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
        val corr = correlationId?.let { " corr=$it" } ?: ""
        val extra = detail?.let { " $it" } ?: ""
        val line = "[PCP:${category.name}] #${n.toString().padStart(4, '0')}$corr $ts WARN $message$extra"
        Log.w(TAG, line)
    }

    fun error(category: Category, message: String, throwable: Throwable? = null) {
        val n = step.incrementAndGet()
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
        val corr = correlationId?.let { " corr=$it" } ?: ""
        val line = "[PCP:${category.name}] #${n.toString().padStart(4, '0')}$corr $ts ERROR $message"
        if (throwable != null) Log.e(TAG, line, throwable) else Log.e(TAG, line)
    }
}
