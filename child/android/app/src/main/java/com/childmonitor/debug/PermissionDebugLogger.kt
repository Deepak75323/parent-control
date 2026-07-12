package com.childmonitor.debug

import android.os.Looper
import android.util.Log
import com.childmonitor.BuildConfig

/** Temporary ANR investigation logs — filter Logcat: PERMISSION_DEBUG (debug builds only). */
object PermissionDebugLogger {

    private const val TAG = "PERMISSION_DEBUG"

    fun enter(method: String, detail: String? = null) {
        if (!BuildConfig.DEBUG) return
        log("ENTER", method, detail)
    }

    fun exit(method: String, detail: String? = null) {
        if (!BuildConfig.DEBUG) return
        log("EXIT", method, detail)
    }

    fun event(method: String, detail: String? = null) {
        if (!BuildConfig.DEBUG) return
        log("EVENT", method, detail)
    }

    fun warn(method: String, detail: String? = null) {
        if (!BuildConfig.DEBUG) return
        val thread = threadLabel()
        val extra = detail?.let { " $it" } ?: ""
        Log.w(TAG, "[$method] WARN $thread$extra")
    }

    fun error(method: String, detail: String? = null) {
        if (!BuildConfig.DEBUG) return
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
        if (Looper.myLooper() == Looper.getMainLooper()) " main" else " bg"
}
