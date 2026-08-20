package com.childmonitor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
)

object LocationReader {

    @SuppressLint("MissingPermission")
    fun getLastKnown(context: Context): LocationSnapshot? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var best: Location? = null
        for (provider in providers) {
            if (!manager.isProviderEnabled(provider)) continue
            val loc = manager.getLastKnownLocation(provider) ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        return best?.let {
            LocationSnapshot(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyM = if (it.hasAccuracy()) it.accuracy else null,
            )
        }
    }

    fun hasPermission(context: Context): Boolean {
        val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
