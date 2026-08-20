package com.childmonitor.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.capture.StreamTeardownCoordinator
import com.childmonitor.sync.HeartbeatSyncManager
import com.childmonitor.sync.DeviceHttpClient
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Monitors network connectivity changes and immediately notifies parent when child device goes offline.
 * This ensures real-time offline detection rather than waiting for heartbeat timeouts.
 */
object NetworkConnectivityMonitor {
    
    private const val TAG = "NetworkConnectivityMonitor"

    /**
     * Wi‑Fi ↔ mobile handoffs briefly clear [ConnectivityManager.activeNetwork] /
     * VALIDATED. Tearing down capture in that window kills the live stream and leaves
     * parent on "Couldn't reconnect". Wait for a stable offline gap before teardown.
     */
    /** Long enough for wifi↔cellular handoff + ICE iceRestart offer (~2–4s). */
    private const val OFFLINE_TEARDOWN_DEBOUNCE_MS = 5000L
    
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val isMonitoring = AtomicBoolean(false)
    private val wasOnline = AtomicBoolean(true) // Assume online initially
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingOfflineTeardown = AtomicReference<Runnable?>(null)
    
    /**
     * Start monitoring network connectivity changes.
     * Will immediately trigger heartbeat when network status changes.
     */
    fun startMonitoring(context: Context) {
        if (isMonitoring.getAndSet(true)) {
            return // Already monitoring
        }
        
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Default network available: $network")
                    handleNetworkChange(context, isNetworkAvailable(context))
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "Default network lost: $network")
                    handleNetworkChange(context, isNetworkAvailable(context))
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val online = isNetworkAvailable(context)
                    Log.d(
                        TAG,
                        "Default network capabilities changed: $network online=$online",
                    )
                    handleNetworkChange(context, online)
                }
            }

            val callback = networkCallback
            if (callback != null) {
                // Track the system default network (Wi‑Fi ↔ cellular handoff) instead of a
                // filtered INTERNET+VALIDATED request — some OEMs never deliver onLost on the
                // filtered callback when airplane mode is enabled, leaving capture stuck on.
                connectivityManager?.registerDefaultNetworkCallback(callback)
            } else {
                Log.w(TAG, "Network monitoring: networkCallback missing; skipping registerDefaultNetworkCallback")
            }
            Log.i(TAG, "Network monitoring started (default network callback)")
        } else {
            // For older Android versions, we'll rely on periodic heartbeat checks
            Log.i(TAG, "Network monitoring not available on Android < N, using periodic checks")
        }
        
        // Initial check
        val currentlyOnline = isNetworkAvailable(context)
        wasOnline.set(currentlyOnline)
        Log.i(TAG, "Initial network state: online=$currentlyOnline")
    }
    
    /**
     * Stop monitoring network connectivity changes.
     */
    fun stopMonitoring() {
        if (!isMonitoring.getAndSet(false)) {
            return // Not monitoring
        }

        cancelPendingOfflineTeardown()
        
        networkCallback?.let { callback ->
            connectivityManager?.unregisterNetworkCallback(callback)
        }
        
        networkCallback = null
        connectivityManager = null
        Log.i(TAG, "Network monitoring stopped")
    }
    
    /**
     * Check if network is currently available.
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.isConnected == true
        }
    }

    private fun cancelPendingOfflineTeardown() {
        pendingOfflineTeardown.getAndSet(null)?.let { mainHandler.removeCallbacks(it) }
    }
    
    /**
     * Handle network connectivity changes.
     * Debounces offline teardown so brief wifi↔mobile flaps do not kill the live stream.
     * Confirmed offline still always releases capture (privacy indicators).
     */
    private fun handleNetworkChange(context: Context, isOnline: Boolean) {
        if (isOnline) {
            cancelPendingOfflineTeardown()
            val previousState = wasOnline.getAndSet(true)
            if (!previousState) {
                Log.i(TAG, "Network state changed: false -> true")
                Log.i(TAG, "Device back online - sending immediate heartbeat")
                try {
                    HeartbeatSyncManager.syncNow(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send online heartbeat", e)
                }
            }
            return
        }

        wasOnline.set(false)
        if (pendingOfflineTeardown.get() != null) {
            Log.d(TAG, "Offline teardown already scheduled — waiting for debounce")
            return
        }

        val appContext = context.applicationContext
        val runnable = Runnable {
            pendingOfflineTeardown.set(null)
            if (isNetworkAvailable(appContext)) {
                Log.i(TAG, "Offline debounce cancelled — internet restored before teardown")
                wasOnline.set(true)
                return@Runnable
            }
            Log.w(TAG, "No validated internet after ${OFFLINE_TEARDOWN_DEBOUNCE_MS}ms — stopping local streams")
            try {
                StreamTeardownCoordinator.requestImmediateStopAll(
                    appContext,
                    "network_lost",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to teardown streams on network loss", e)
            }
            notifySessionsAboutNetworkLoss(appContext)
        }
        pendingOfflineTeardown.set(runnable)
        Log.w(
            TAG,
            "No validated internet — debouncing stream teardown ${OFFLINE_TEARDOWN_DEBOUNCE_MS}ms",
        )
        mainHandler.postDelayed(runnable, OFFLINE_TEARDOWN_DEBOUNCE_MS)
    }
    
    /**
     * Get current network connectivity status.
     */
    fun isCurrentlyOnline(context: Context): Boolean {
        return isNetworkAvailable(context)
    }
    
    /**
     * Notify backend about network loss so streaming sessions can be cleaned up immediately.
     * This prevents sessions from staying in PENDING/ACTIVE state when child is unreachable.
     */
    private fun notifySessionsAboutNetworkLoss(context: Context) {
        try {
            // Try to send a final network loss notification if we have any cached connectivity
            // This will fail but allows the HttpClient to detect the network loss state
            val body = org.json.JSONObject()
                .put("type", "NETWORK_LOST")
                .put("timestamp", System.currentTimeMillis())
                
            DeviceHttpClient.postJsonDetailed(context, "/v1/device/network-status", body.toString())
            
            // The above call will fail with NoNetwork, but that's expected and handled appropriately
        } catch (e: Exception) {
            Log.w(TAG, "Expected failure notifying network loss", e)
        }
    }
}