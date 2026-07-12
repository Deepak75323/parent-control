package com.childmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.childmonitor.monitoring.BootRecoveryTelemetry
import com.childmonitor.monitoring.BootWakeChain

/**
 * When network becomes available shortly after boot, run fast recovery immediately.
 */
class NetworkRecoveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ConnectivityManager.CONNECTIVITY_ACTION) return
        if (!MonitoringPrefs.isPaired(context)) return
        if (!BootRecoveryTelemetry.isWithinPostBootWindow(context)) return
        if (!isNetworkConnected(context)) return

        Log.i(TAG, "Network available post-boot — fast recovery")
        BootWakeChain.runFastRecoveryNow(context.applicationContext, BootWakeChain.Trigger.NETWORK_AVAILABLE)
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "NetworkRecoveryReceiver"
    }
}
