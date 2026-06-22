package com.childmonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeofenceMonitor {

    private val insideState = mutableMapOf<String, Boolean>()

    fun checkLocation(context: Context, lat: Double, lon: Double) {
        val bundle = PolicyStore.getBundle(context) ?: return
        val geofences = bundle.optJSONArray("geofences") ?: return

        for (i in 0 until geofences.length()) {
            val g = geofences.getJSONObject(i)
            if (!g.optBoolean("enabled", true)) continue
            val id = g.getString("ruleId")
            val name = g.getString("name")
            val inside = haversineM(lat, lon, g.getDouble("latitude"), g.getDouble("longitude")) <= g.getDouble("radiusM")
            val wasInside = insideState[id] ?: false

            when {
                inside && !wasInside && g.optBoolean("alertOnEnter", true) -> {
                    PolicyViolationUploader.upload(context, "GEOFENCE_ENTER", null, g.optString("policyId"), mapOf("geofenceName" to name))
                }
                !inside && wasInside && g.optBoolean("alertOnExit", true) -> {
                    PolicyViolationUploader.upload(context, "GEOFENCE_EXIT", null, g.optString("policyId"), mapOf("geofenceName" to name))
                }
            }
            insideState[id] = inside
        }
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
