package com.childmonitor.sync

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves a reachable dev API base URL for native HTTP when the stored URL is stale
 * (e.g. USB adb reverse vs Wi‑Fi LAN).
 */
object NativeApiResolver {

    private const val TAG = "NativeApiResolver"
    private const val DEFAULT_LAN_HOST = "192.168.1.5"

    fun candidates(context: Context): List<String> {
        val saved = MonitoringPrefs.apiBaseUrl(context)?.trimEnd('/')
        return listOfNotNull(
            saved,
            "http://127.0.0.1:8080",
            "http://$DEFAULT_LAN_HOST:8080",
            "http://10.0.2.2:8080",
        ).distinct()
    }

    /** Returns a working base URL, updating prefs when a better candidate is found. */
    fun resolveWorkingBase(context: Context): String? {
        for (base in candidates(context)) {
            if (probeHealth(base)) {
                val current = MonitoringPrefs.apiBaseUrl(context)?.trimEnd('/')
                if (current != base) {
                    Log.i(TAG, "Switching API base $current -> $base")
                    MonitoringPrefs.updateApiBaseUrl(context, base)
                    NativeStompOwner.onApiBaseUrlChanged(context)
                }
                return base
            }
        }
        Log.w(TAG, "No reachable API base (tried ${candidates(context)})")
        return null
    }

    private fun probeHealth(baseUrl: String): Boolean {
        return try {
            val conn = URL("$baseUrl/actuator/health").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }
}
