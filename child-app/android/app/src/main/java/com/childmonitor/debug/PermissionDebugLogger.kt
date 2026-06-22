package com.childmonitor.debug

import android.os.Looper
import android.util.Log

/** Temporary ANR investigation logs — filter Logcat: PERMISSION_DEBUG */
object PermissionDebugLogger {

    private const val TAG = "PERMISSION_DEBUG"

    fun enter(method: String, detail: String? = null) {
        log("ENTER", method, detail)
    }

    fun exit(method: String, detail: String? = null) {
        log("EXIT", method, detail)
    }

    fun event(method: String, detail: String? = null) {
        log("EVENT", method, detail)
    }

    fun warn(method: String, detail: String? = null) {
        val thread = threadLabel()
        val extra = detail?.let { " $it" } ?: ""
        Log.w(TAG, "[$method] WARN $thread$extra")
    }

    fun error(method: String, detail: String? = null) {
        val thread = threadLabel()
        val extra = detail?.let { " $it" } ?: ""
        Log.e(TAG, "[$method] ERROR $thread$extra")
    }

    private fun log(phase: String, method: String, detail: String?) {
        val thread = threadLabel()
        val extra = detail?.let { " $it" } ?: ""
        Log.i(TAG, "[$method] $phase $thread$extra")
    }

    private fun threadLabel(): String =
        "thread=${Thread.currentThread().name} main=${Looper.myLooper() == Looper.getMainLooper()}"
}
