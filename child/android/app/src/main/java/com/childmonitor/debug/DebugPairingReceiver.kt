package com.childmonitor.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.TamperProtectionManager
import com.childmonitor.monitoring.MonitoringServiceLauncher

/**
 * Dev/automation only — apply pairing credentials from adb broadcast (no UI).
 *
 * adb shell am broadcast -a com.childmonitor.DEBUG_APPLY_PAIR \
 *   --es apiBaseUrl "http://127.0.0.1:8080" \
 *   --es deviceToken "..." \
 *   --es deviceHmacKey "..." \
 *   --es parentDisplayName "Jane Doe"
 */
class DebugPairingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!com.childmonitor.BuildConfig.DEBUG) {
            Log.w(TAG, "DEBUG_APPLY_PAIR ignored — not a debug build")
            return
        }
        if (intent?.action != ACTION) return
        val token = intent.getStringExtra(EXTRA_TOKEN)?.trim().orEmpty()
        val hmac = intent.getStringExtra(EXTRA_HMAC)?.trim().orEmpty()
        val api = intent.getStringExtra(EXTRA_API)?.trim().orEmpty()
            .ifBlank { "http://127.0.0.1:8080" }
        val parentName = intent.getStringExtra(EXTRA_PARENT_NAME)

        if (token.isBlank()) {
            Log.e(TAG, "DEBUG_APPLY_PAIR missing deviceToken")
            return
        }

        MonitoringPrefs.saveSyncConfig(context.applicationContext, api, token, hmac, parentName)
        TamperProtectionManager.applyStoredTamperSettings(context.applicationContext)
        MonitoringServiceLauncher.onDevicePaired(context.applicationContext)
        Log.i(TAG, "DEBUG_APPLY_PAIR saved credentials api=$api parent=$parentName")
    }

    companion object {
        private const val TAG = "DebugPairingReceiver"
        const val ACTION = "com.childmonitor.DEBUG_APPLY_PAIR"
        const val EXTRA_TOKEN = "deviceToken"
        const val EXTRA_HMAC = "deviceHmacKey"
        const val EXTRA_API = "apiBaseUrl"
        const val EXTRA_PARENT_NAME = "parentDisplayName"
    }
}
