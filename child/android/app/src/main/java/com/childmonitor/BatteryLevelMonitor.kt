package com.childmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * Keeps a live battery percent from [Intent.ACTION_BATTERY_CHANGED] events.
 *
 * On some Xiaomi/HyperOS builds the sticky battery intent and
 * [android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY] freeze (often at a value like 93),
 * while real [BroadcastReceiver] callbacks still move. Heartbeats then prefer [latestPercent].
 */
object BatteryLevelMonitor {

    private const val TAG = "BatteryLevelMonitor"

    @Volatile
    private var latestPercent: Int? = null

    @Volatile
    private var registered = false

    private val lock = Any()

    fun latestPercent(): Int? = latestPercent

    fun start(context: Context) {
        synchronized(lock) {
            if (registered) {
                return
            }
            val app = context.applicationContext
            seedFromSticky(app)
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        DeviceTelemetryReader.percentFromBatteryIntent(intent)?.let { pct ->
                            latestPercent = pct
                        }
                    }
                }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
            registered = true
            Log.i(TAG, "listening for battery changes seed=$latestPercent")
        }
    }

    /** Package-visible for tests. */
    internal fun resetForTests() {
        synchronized(lock) {
            latestPercent = null
            registered = false
        }
    }

    /** Package-visible for tests. */
    internal fun setLatestForTests(percent: Int?) {
        latestPercent = percent
    }

    private fun seedFromSticky(context: Context) {
        val sticky = queryStickyBatteryIntent(context)
        latestPercent = DeviceTelemetryReader.percentFromBatteryIntent(sticky)
    }

    fun queryStickyBatteryIntent(context: Context): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(null, filter)
        }
    }
}
