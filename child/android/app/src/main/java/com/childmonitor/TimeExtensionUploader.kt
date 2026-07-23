package com.childmonitor

import android.content.Context
import com.childmonitor.constants.ApiPaths
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONObject

object TimeExtensionUploader {

    fun requestMoreTime(
        context: Context,
        packageName: String,
        appLabel: String?,
        requestedMinutes: Int = 30,
    ): Boolean {
        val body = JSONObject().apply {
            put("packageName", packageName)
            if (appLabel != null) put("appLabel", appLabel)
            put("requestedMinutes", requestedMinutes)
        }
        return DeviceHttpClient.postJson(context, ApiPaths.DEVICE_TIME_EXTENSION_REQUESTS, body.toString())
    }
}
