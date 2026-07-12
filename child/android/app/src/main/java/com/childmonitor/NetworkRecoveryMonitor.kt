package com.childmonitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.monitoring.BootRecoveryTelemetry
import com.childmonitor.monitoring.BootWakeChain

/**
 * P1-03: Modern replacement for the obsolete manifest
 * [ConnectivityManager.CONNECTIVITY_ACTION] receiver.
 *
 * Android 7+ apps no longer receive CONNECTIVITY_ACTION via dynamic registration for
 * most cases; Android 10+ (API 29+) apps targeting API 29+ never receive it from a
 * **manifest-registered** receiver. This monitor uses
 * [ConnectivityManager.NetworkCallback] instead — the supported API through Android 16.
 *
 * Lifecycle:
 * - Started from [BootWakeChain] after BOOT and from [MainApplication] when the process
 *   restarts inside the post-boot window.
 * - Fires [BootWakeChain.runFastRecoveryNow] when a validated internet network appears
 *   (Wi‑Fi, cellular, Ethernet) while still inside the post-boot window.
 * - Auto-unregisters when the post-boot window expires or the device is unpaired.
 *
 * Does **not** replace the STOMP reconnect callback inside
 * [MonitoringForegroundService] — that handles mid-session network flips while the FGS
 * is already running.
 */
object NetworkRecoveryMonitor {

    private const val TAG = "NetworkRecoveryMonitor"
    private const val DEBOUNCE_MS = 5_000L
    /** How often to re-check whether we should still listen (post-boot window). */
    private const val WINDOW_CHECK_MS = 60_000L

    @Volatile
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastFiredAtMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val windowCheckRunnable = object : Runnable {
        override fun run() {
            val app = appContext ?: return
            if (!shouldRemainActive(app)) {
                Log.i(TAG, "post-boot window ended or unpaired — stopping NetworkCallback")
                stop(app)
                return
            }
            mainHandler.postDelayed(this, WINDOW_CHECK_MS)
        }
    }

    @Volatile
    private var appContext: Context? = null

    /**
     * Register a [ConnectivityManager.NetworkCallback] when paired and inside (or about
     * to enter) the post-boot recovery window. Safe to call repeatedly — idempotent.
     */
    @Synchronized
    fun start(context: Context, reason: String) {
        val app = context.applicationContext
        if (!MonitoringPrefs.isPaired(app)) {
            Log.d(TAG, "start skipped — not paired reason=$reason")
            return
        }
        // Allow start immediately after BOOT before telemetry window is recorded, or
        // while still inside the 10-minute post-boot window.
        val withinWindow = BootRecoveryTelemetry.isWithinPostBootWindow(app)
        val bootRecorded = BootRecoveryTelemetry.bootReceivedAtMs(app) > 0L
        if (!withinWindow && bootRecorded) {
            Log.d(TAG, "start skipped — outside post-boot window reason=$reason")
            return
        }
        if (!withinWindow && !bootRecorded && reason != "boot") {
            Log.d(TAG, "start skipped — no boot telemetry yet reason=$reason")
            return
        }

        if (callback != null) {
            Log.d(TAG, "already registered reason=$reason")
            return
        }

        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            Log.w(TAG, "ConnectivityManager unavailable reason=$reason")
            return
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // onAvailable alone is not enough on Android 10+ — captive portals /
                // airplane-mode transitions may report a network before it is validated.
                Log.d(TAG, "onAvailable network=$network — waiting for VALIDATED")
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!isUsableInternet(caps)) return
                val transport = describeTransport(caps)
                Log.i(TAG, "validated network available transport=$transport network=$network")
                maybeFireRecovery(app, "validated_$transport")
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "network lost network=$network")
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.BOOT_RECOVERY,
                    "network_lost",
                    "network=$network",
                )
            }
        }

        try {
            // registerNetworkCallback works on API 21+; minSdk is 24.
            // Prefer default-network callback on API 24+ so we track the system default
            // path (Wi‑Fi ↔ cellular handoff) rather than every underlying network.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(cb)
            } else {
                cm.registerNetworkCallback(request, cb)
            }
            callback = cb
            appContext = app
            mainHandler.removeCallbacks(windowCheckRunnable)
            mainHandler.postDelayed(windowCheckRunnable, WINDOW_CHECK_MS)

            PcpFlowLogger.log(
                PcpFlowLogger.Category.BOOT_RECOVERY,
                "network_monitor_started",
                "reason=$reason api=${Build.VERSION.SDK_INT}",
            )
            Log.i(TAG, "NetworkCallback registered reason=$reason api=${Build.VERSION.SDK_INT}")

            // If network is already up (Wi‑Fi connected at boot), fire immediately.
            if (hasUsableInternet(app)) {
                maybeFireRecovery(app, "already_connected_at_start")
            }
        } catch (e: Exception) {
            callback = null
            appContext = null
            Log.w(TAG, "NetworkCallback registration failed reason=$reason: ${e.message}")
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.BOOT_RECOVERY,
                "network_monitor_register_failed",
                "reason=$reason error=${e.message}",
            )
        }
    }

    /** Unregister the callback. Idempotent. */
    @Synchronized
    fun stop(context: Context) {
        mainHandler.removeCallbacks(windowCheckRunnable)
        val cb = callback ?: return
        val app = context.applicationContext
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
            Log.i(TAG, "NetworkCallback unregistered")
            PcpFlowLogger.log(PcpFlowLogger.Category.BOOT_RECOVERY, "network_monitor_stopped", "")
        } catch (e: Exception) {
            Log.w(TAG, "unregisterNetworkCallback failed: ${e.message}")
        } finally {
            callback = null
            appContext = null
        }
    }

    fun isRegistered(): Boolean = callback != null

    /** True when the default network has INTERNET + VALIDATED (or INTERNET pre-API 23). */
    fun hasUsableInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return isUsableInternet(caps)
    }

    /** @visibleForTesting */
    internal fun isUsableInternet(caps: NetworkCapabilities): Boolean {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        // VALIDATED exists from API 23; without it we still accept INTERNET-capable networks.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        return true
    }

    /** @visibleForTesting */
    internal fun describeTransport(caps: NetworkCapabilities): String = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        else -> "other"
    }

    /** @visibleForTesting */
    internal fun resetForTests() {
        mainHandler.removeCallbacksAndMessages(null)
        callback = null
        appContext = null
        lastFiredAtMs = 0L
    }

    private fun shouldRemainActive(context: Context): Boolean =
        MonitoringPrefs.isPaired(context) &&
            BootRecoveryTelemetry.isWithinPostBootWindow(context)

    private fun maybeFireRecovery(context: Context, detail: String) {
        if (!MonitoringPrefs.isPaired(context)) {
            stop(context)
            return
        }
        if (!BootRecoveryTelemetry.isWithinPostBootWindow(context)) {
            Log.d(TAG, "skip recovery — outside post-boot window detail=$detail")
            stop(context)
            return
        }

        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastFiredAtMs < DEBOUNCE_MS) {
                Log.d(TAG, "debounced network recovery detail=$detail")
                return
            }
            lastFiredAtMs = now
        }

        PcpFlowLogger.log(
            PcpFlowLogger.Category.BOOT_RECOVERY,
            "network_available_post_boot",
            "detail=$detail api=${Build.VERSION.SDK_INT}",
        )
        Log.i(TAG, "Network available post-boot — fast recovery detail=$detail")
        BootWakeChain.runFastRecoveryNow(
            context.applicationContext,
            BootWakeChain.Trigger.NETWORK_AVAILABLE,
        )
    }
}
