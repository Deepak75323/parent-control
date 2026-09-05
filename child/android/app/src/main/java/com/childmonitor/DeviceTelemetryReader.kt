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

    /**
     * Prefer sticky [Intent.ACTION_BATTERY_CHANGED] over [BatteryManager.BATTERY_PROPERTY_CAPACITY].
     * On some Xiaomi/HyperOS builds CAPACITY can stick at a stale value while the sticky
     * broadcast still tracks the real level — which made parent UI show Online/Offline
     * updates with a frozen battery percent.
     */
    private fun readBattery(context: Context): Pair<Int?, Boolean> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val stickyPercent = percentFromBatteryIntent(sticky)
        val capacityProperty = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val normalized =
            resolveBatteryPercent(stickyPercent, capacityProperty, lastKnownBatteryLevel)
        if (normalized != null) {
            lastKnownBatteryLevel = normalized
        }

        val charging = chargingFromSources(bm, sticky)
        return normalized to charging
    }

    internal fun percentFromBatteryIntent(intent: Intent?): Int? {
        if (intent == null) return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }

    internal fun resolveBatteryPercent(
        stickyPercent: Int?,
        capacityProperty: Int,
        lastKnown: Int?,
    ): Int? {
        stickyPercent?.takeIf { it in 0..100 }?.let { return it }
        if (capacityProperty in 0..100) return capacityProperty
        return lastKnown?.takeIf { it in 0..100 }
    }

    private fun chargingFromSources(bm: BatteryManager, sticky: Intent?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bm.isCharging) {
            return true
        }
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING
            || status == BatteryManager.BATTERY_STATUS_FULL
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
