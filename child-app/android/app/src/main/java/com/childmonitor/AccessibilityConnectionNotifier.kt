package com.childmonitor

import android.content.Context
import android.util.Log
import com.childmonitor.permissions.PermissionSettingsLauncher

/**
 * Pushes immediate heartbeats when the accessibility service connects or disconnects so the
 * parent dashboard reflects runtime state without waiting for the 5-minute sync interval.
 */
object AccessibilityConnectionNotifier {

    private const val TAG = "AccessibilityConnNotify"

    fun onServiceConnected(context: Context) {
        Log.i(TAG, "Accessibility service connected — notifying server")
        Thread {
            NativeSyncUploader.syncAccessibilityServiceState(context.applicationContext, connected = true)
        }.start()
        com.childmonitor.native.ChildMonitorNativeModule.emitPermissionStateChanged()
    }

    fun onServiceDisconnected(context: Context) {
        Log.i(TAG, "Accessibility service disconnected — clearing screen capture + notifying server")
        PermissionSettingsLauncher.clearScreenCaptureConsent(context.applicationContext)
        Thread {
            NativeSyncUploader.syncAccessibilityServiceState(context.applicationContext, connected = false)
        }.start()
        com.childmonitor.native.ChildMonitorNativeModule.emitPermissionStateChanged()
    }
}
