package com.childmonitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

object DeviceTelemetryReader {

    data class Snapshot(
        val batteryLevel: Int?,
        val charging: Boolean,
        val networkType: String,
    )

    @Volatile
    private var lastKnownBatteryLevel: Int? = null

    fun read(context: Context): Snapshot {
        val battery = readBattery(context)
        return Snapshot(
            batteryLevel = battery.first,
            charging = battery.second,
            networkType = readNetworkType(context),
        )
    }

    private fun readBattery(context: Context): Pair<Int?, Boolean> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        var pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (pct !in 0..100) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                pct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                pct = lastKnownBatteryLevel ?: -1
            }
        }
        val charging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bm.isCharging
        } else {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
        }
        val normalized = pct.takeIf { it in 0..100 }
        if (normalized != null) {
            lastKnownBatteryLevel = normalized
        }
        return normalized to charging
    }

    private fun readNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "NONE"
        val caps = cm.getNetworkCapabilities(network) ?: return "UNKNOWN"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }
}
