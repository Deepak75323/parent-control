package com.childmonitor.capture

import android.content.Context
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONArray
import org.json.JSONObject
import com.childmonitor.constants.ApiPaths

object SignalingHttpClient {

    fun postMessage(
        context: Context,
        roomId: String,
        messageType: String,
        payload: String,
        sessionId: String,
    ): Boolean {
        require(sessionId.isNotBlank()) { "sessionId required for signaling" }
        val body = JSONObject()
            .put("messageType", messageType)
            .put("payload", payload)
            .put("sessionId", sessionId)

        return DeviceHttpClient.postJson(
            context,
            ApiPaths.signaling(roomId),
            body.toString(),
        )
    }

    fun fetchRecent(context: Context, roomId: String): JSONArray? {
        val text = DeviceHttpClient.get(context, ApiPaths.signaling(roomId)) ?: return null
        return try {
            val root = JSONObject(text)
            when (val data = root.opt("data")) {
                is JSONArray -> data
                is JSONObject -> data.optJSONArray("content") ?: JSONArray()
                else -> JSONArray()
            }
        } catch (_: Exception) {
            null
        }
    }
}
