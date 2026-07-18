package com.childmonitor.sync

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves a reachable API base URL for native HTTP when the stored URL is stale
 * (e.g. USB adb reverse vs Wi‑Fi LAN).
 *
 * For non-local (staging/production) bases, only the configured URL is used — probing
 * localhost / emulator addresses on every heartbeat would amplify nginx load during
 * outages and was part of the 2026-07-14 connection storm.
 */
object NativeApiResolver {

    private const val TAG = "NativeApiResolver"
    private const val DEFAULT_LAN_HOST = "192.168.1.5"
    private const val CACHE_TTL_MS = 60_000L

    @Volatile
    private var cachedBase: String? = null

    @Volatile
    private var cachedAtMs = 0L

    fun candidates(context: Context): List<String> {
        val saved = MonitoringPrefs.apiBaseUrl(context)?.trimEnd('/')
        if (saved != null && !isLocalDevHost(saved)) {
            return listOf(saved)
        }
        return listOfNotNull(
            saved,
            "http://127.0.0.1:8080",
            "http://$DEFAULT_LAN_HOST:8080",
            "http://10.0.2.2:8080",
        ).distinct()
    }

    /** Returns a working base URL, updating prefs when a better candidate is found. */
    fun resolveWorkingBase(context: Context): String? {
        val now = System.currentTimeMillis()
        val cached = cachedBase
        if (cached != null && now - cachedAtMs < CACHE_TTL_MS) {
            return cached
        }

        for (base in candidates(context)) {
            if (probeHealth(base)) {
                cachedBase = base
                cachedAtMs = now
                val current = MonitoringPrefs.apiBaseUrl(context)?.trimEnd('/')
                if (current != base) {
                    Log.i(TAG, "Switching API base $current -> $base")
                    MonitoringPrefs.updateApiBaseUrl(context, base)
                    NativeStompOwner.onApiBaseUrlChanged(context)
                }
                return base
            }
        }
        cachedBase = null
        cachedAtMs = 0L
        Log.w(TAG, "No reachable API base (tried ${candidates(context)})")
        return null
    }

    /** Drop cached base so the next [resolveWorkingBase] re-probes. */
    fun invalidateCache() {
        cachedBase = null
        cachedAtMs = 0L
    }

    internal fun isLocalDevHost(baseUrl: String): Boolean {
        val host = baseUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
            .lowercase()
        return host == "127.0.0.1" ||
            host == "localhost" ||
            host == "10.0.2.2" ||
            host == DEFAULT_LAN_HOST ||
            host.startsWith("192.168.") ||
            host.startsWith("10.")
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
