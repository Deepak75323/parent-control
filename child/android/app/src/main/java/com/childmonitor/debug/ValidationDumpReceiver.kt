package com.childmonitor.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.BuildConfig
import com.childmonitor.oem.OemDiagnosticsCollector
import org.json.JSONObject

/**
 * Debug automation: dumps validation precheck state as a single JSON log line.
 *
 * adb shell am broadcast -a com.childmonitor.VALIDATION_DUMP
 * adb logcat -d | grep OEM_VALIDATION
 */
class ValidationDumpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG && !isCertificationBuild()) {
            Log.w(TAG, "VALIDATION_DUMP ignored — not a debug/certification build")
            return
        }
        val diagnostics = OemDiagnosticsCollector.collect(context.applicationContext)
        val json = JSONObject(diagnostics as Map<*, *>)
            .put("type", "OEM_VALIDATION_DUMP")
            .put("package", context.applicationContext.packageName)
            .put("autostartEnabled", JSONObject.NULL)
            .put("recentsLocked", JSONObject.NULL)
            .put("note", "autostart/recentsLocked require manual OEM confirmation or override JSON")

        Log.i(TAG, json.toString())
    }

    private fun isCertificationBuild(): Boolean {
        return try {
            BuildConfig::class.java.getField("CERTIFICATION_BUILD").getBoolean(null)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "OEM_VALIDATION"
        const val ACTION = "com.childmonitor.VALIDATION_DUMP"
    }
}
