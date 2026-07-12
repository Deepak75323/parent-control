package com.childmonitor.capture

import android.content.Context
import android.util.Log
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import com.childmonitor.constants.ApiPaths

object WebrtcConfigLoader {

    private const val TAG = "WebrtcConfigLoader"

    @JvmStatic
    @JvmOverloads
    fun loadIceServers(context: Context, sessionId: String? = null): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        // P2-4: when a session is in flight, request session-bound TURN credentials so a
        // leaked username/password can only relay this one stream (≤15 min lifetime).
        val path = if (!sessionId.isNullOrBlank()) {
            ApiPaths.webrtcConfig(sessionId)
        } else {
            ApiPaths.MONITORING_WEBRTC_CONFIG
        }
        val body = DeviceHttpClient.get(context, path) ?: return fallbackStunOnly()
        try {
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: return fallbackStunOnly()
            val iceArray = data.optJSONArray("iceServers") ?: return fallbackStunOnly()
            for (i in 0 until iceArray.length()) {
                val entry = iceArray.optJSONObject(i) ?: continue
                val urlsJson = entry.opt("urls")
                val urls = when (urlsJson) {
                    is JSONArray -> (0 until urlsJson.length()).mapNotNull { urlsJson.optString(it).takeIf { u -> u.isNotBlank() } }
                    is String -> listOf(urlsJson)
                    else -> emptyList()
                }
                if (urls.isEmpty()) continue
                val builder = PeerConnection.IceServer.builder(urls)
                val user = entry.optString("username", "")
                val cred = entry.optString("credential", "")
                if (user.isNotBlank() && cred.isNotBlank()) {
                    builder.setUsername(user).setPassword(cred)
                }
                servers.add(builder.createIceServer())
            }
            Log.i(TAG, "Loaded ${servers.size} ICE servers from API")
            if (servers.isEmpty()) return fallbackStunOnly()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse webrtc-config", e)
            return fallbackStunOnly()
        }
        return servers
    }

    private fun fallbackStunOnly(): List<PeerConnection.IceServer> {
        Log.w(TAG, "Using STUN-only fallback — TURN requires /webrtc-config")
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        )
    }
}
