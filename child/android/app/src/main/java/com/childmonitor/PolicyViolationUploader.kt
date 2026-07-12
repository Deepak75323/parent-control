package com.childmonitor

import android.content.Context
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONObject
import com.childmonitor.constants.ApiPaths

object PolicyViolationUploader {

    fun upload(
        context: Context,
        violationType: String,
        packageName: String?,
        policyId: String?,
        details: Map<String, Any>?,
    ): Boolean {
        val body = JSONObject().apply {
            put("violationType", violationType)
            if (packageName != null) put("packageName", packageName)
            if (policyId != null) put("policyId", policyId)
            if (details != null) put("details", JSONObject(details))
        }.toString()
        return DeviceHttpClient.postJson(context, ApiPaths.EVENTS_POLICY_VIOLATIONS, body)
    }
}
