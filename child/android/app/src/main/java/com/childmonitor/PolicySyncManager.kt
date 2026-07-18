package com.childmonitor

import android.content.Context
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONObject
import com.childmonitor.constants.ApiPaths

object PolicySyncManager {

    private const val TAG = "PolicySyncManager"

    fun syncPolicies(context: Context): Boolean {
        val json = DeviceHttpClient.get(context, ApiPaths.CHILD_POLICIES) ?: run {
            android.util.Log.w(TAG, "syncPolicies — GET policies returned null")
            return false
        }

        return try {
            val root = JSONObject(json)
            val data = root.getJSONObject("data")
            val version = data.getString("policyVersion")
            val screenTimeCount = data.optJSONArray("screenTime")?.length() ?: 0
            val appBlockCount = data.optJSONArray("appBlocks")?.length() ?: 0
            PolicyStore.saveBundle(context, data.toString(), version)
            // Always reload enforcement engine from the freshly saved bundle so
            // blocking rules take effect immediately, even if the ack POST fails.
            PolicyEnforcementEngine.reload(context)
            AppBlockingAccessibilityService.enforceCurrentForegroundIfBlocked()
            android.util.Log.i(
                TAG,
                "syncPolicies ok version=$version screenTime=$screenTimeCount appBlocks=$appBlockCount",
            )

            val ackBody = JSONObject().put("policyVersion", version).toString()
            DeviceHttpClient.postJson(context, ApiPaths.CHILD_POLICIES_ACK, ackBody)
            true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "syncPolicies failed", e)
            false
        }
    }
}
