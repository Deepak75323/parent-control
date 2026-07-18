package com.childmonitor

import android.content.Context
import android.util.Log

/**
 * Pushes immediate heartbeats when the accessibility service connects or disconnects so the
 * parent dashboard reflects runtime state without waiting for the 5-minute sync interval.
 *
 * Does **not** launch MediaProjection / Cast on connect — parent must press Start Screen
 * (or complete setup) to request consent.
 */
object AccessibilityConnectionNotifier {

    private const val TAG = "AccessibilityConnNotify"

    fun onServiceConnected(context: Context) {
        Log.i(TAG, "Accessibility service connected — notifying server (no Cast auto-recovery)")
        Thread {
            val app = context.applicationContext
            NativeSyncUploader.syncAccessibilityServiceState(app, connected = true)
        }.start()
        com.childmonitor.native.ChildMonitorNativeModule.emitPermissionStateChanged()
    }

    fun onServiceDisconnected(context: Context) {
        // Do NOT clear MediaProjection consent/token here. Accessibility only drives
        // auto-approve of the Cast dialog; a live/stored projection binder is independent.
        // Force-stop/reinstall often tears down a11y briefly — wiping consent there left the
        // child stuck in RECOVERY_* until manual re-setup, and parent timed out with
        // "Child screen capture did not start".
        Log.i(TAG, "Accessibility service disconnected — notifying server (projection consent preserved)")
        Thread {
            NativeSyncUploader.syncAccessibilityServiceState(context.applicationContext, connected = false)
        }.start()
        com.childmonitor.native.ChildMonitorNativeModule.emitPermissionStateChanged()
    }
}
