package com.childmonitor

import android.app.AppOpsManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.childmonitor.sync.HeartbeatSyncManager

/**
 * Pushes an immediate heartbeat when any permission or OEM readiness setting changes so the parent
 * dashboard does not show stale grants until the next 5-minute alarm.
 */
object DeviceReadinessSyncObserver {

    private const val TAG = "ReadinessSyncObserver"
    private const val DEBOUNCE_MS = 750L

    @Volatile
    private var registered = false

    private var settingsObserver: ContentObserver? = null
    private var appOpsListener: AppOpsManager.OnOpChangedListener? = null
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    fun register(context: Context) {
        synchronized(this) {
            if (registered) return
            val app = context.applicationContext
            registerSettingsObservers(app)
            registerAppOpsListeners(app)
            registered = true
            Log.i(TAG, "registered readiness sync observers")
        }
    }

    /** Debounced heartbeat — safe to call from any thread. */
    fun scheduleHeartbeat(context: Context, reason: String) {
        val app = context.applicationContext
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        debounceRunnable =
            Runnable {
                if (!MonitoringPrefs.isPaired(app)) return@Runnable
                Log.i(TAG, "readiness changed ($reason) — syncing heartbeat")
                Thread {
                    HeartbeatSyncManager.syncNow(app)
                    if (!AccessibilityHealthChecker.isAccessibilityEnabled(app)) {
                        AccessibilityHealthChecker.reportIfDisabled(app)
                    }
                }.start()
            }
        debounceHandler.postDelayed(debounceRunnable!!, DEBOUNCE_MS)
    }

    private fun registerSettingsObservers(context: Context) {
        val observer =
            object : ContentObserver(debounceHandler) {
                override fun onChange(selfChange: Boolean) {
                    scheduleHeartbeat(context, "settings")
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    scheduleHeartbeat(context, "settings:${uri?.lastPathSegment ?: "?"}")
                }
            }
        val resolver = context.contentResolver
        for (key in WATCHED_SECURE_SETTINGS) {
            resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        }
        settingsObserver = observer
    }

    private fun registerAppOpsListeners(context: Context) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val pkg = context.packageName
        val listener =
            AppOpsManager.OnOpChangedListener { op, packageName ->
                if (packageName != null && packageName != pkg) return@OnOpChangedListener
                scheduleHeartbeat(context, "appops:$op")
            }
        appOpsListener = listener
        for (op in WATCHED_APP_OPS) {
            try {
                @Suppress("DEPRECATION")
                appOps.startWatchingMode(op, pkg, listener)
            } catch (e: Exception) {
                Log.w(TAG, "startWatchingMode failed op=$op", e)
            }
        }
    }

    private val WATCHED_SECURE_SETTINGS =
        listOf(
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            "enabled_notification_listeners",
        )

    private val WATCHED_APP_OPS: List<String> =
        buildList {
            add(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW)
            add(AppOpsManager.OPSTR_CAMERA)
            add(AppOpsManager.OPSTR_RECORD_AUDIO)
            add(AppOpsManager.OPSTR_GET_USAGE_STATS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add("android:run_in_background")
                add("android:run_any_in_background")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add("android:post_notification")
            }
        }
}
