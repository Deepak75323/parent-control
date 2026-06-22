package com.childmonitor

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TimeExtensionUploader {

    fun requestMoreTime(
        context: Context,
        packageName: String,
        appLabel: String?,
        requestedMinutes: Int = 30,
    ): Boolean {
        val apiBase = MonitoringPrefs.apiBaseUrl(context) ?: return false
        val token = MonitoringPrefs.deviceToken(context) ?: return false

        return try {
            val body = JSONObject().apply {
                put("packageName", packageName)
                if (appLabel != null) put("appLabel", appLabel)
                put("requestedMinutes", requestedMinutes)
            }

            val conn = URL("$apiBase/api/v1/device/time-extension-requests").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }
}
