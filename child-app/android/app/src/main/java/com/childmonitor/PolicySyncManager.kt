package com.childmonitor

import android.content.Context
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONObject

object PolicySyncManager {

    fun syncPolicies(context: Context): Boolean {
        val json = DeviceHttpClient.get(context, "/api/v1/child/policies") ?: return false

        return try {
            val root = JSONObject(json)
            val data = root.getJSONObject("data")
            val version = data.getString("policyVersion")
            PolicyStore.saveBundle(context, data.toString(), version)
            // Always reload enforcement engine from the freshly saved bundle so
            // blocking rules take effect immediately, even if the ack POST fails.
            PolicyEnforcementEngine.reload(context)

            val ackBody = JSONObject().put("policyVersion", version).toString()
            DeviceHttpClient.postJson(context, "/api/v1/child/policies/ack", ackBody)
        } catch (_: Exception) {
            false
        }
    }
}
