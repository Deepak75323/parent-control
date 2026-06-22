package com.childmonitor.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.view.WindowCompat
import com.childmonitor.capture.MediaProjectionConsentHelper
import com.childmonitor.capture.ScreenFgsStartupGate
import com.childmonitor.capture.StreamBootstrapActivity
import com.childmonitor.monitoring.MonitoringServiceLauncher

/**
 * Platform-specific fallbacks and safe launch paths for Android 15/16 restrictions.
 */
object Android16CompatibilityLayer {

    private const val TAG = "Android16Compat"

    fun applyWindowInsets(activity: Activity) {
        if (!Android16FeatureFlags.isEdgeToEdgeEnabled(activity)) return
        if (!AndroidPlatform.enforcesEdgeToEdge()) return
        try {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            Log.i(TAG, "edge-to-edge enabled for ${activity.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.w(TAG, "edge-to-edge setup failed", e)
        }
    }

    /**
     * Safe path to wake stream UI when background activity launch may be blocked.
     * Falls back to headless service when direct activity start is not allowed.
     */
    fun launchStreamBootstrap(context: Context, roomId: String, sessionId: String, streamType: String) {
        val appContext = context.applicationContext
        if (!AndroidPlatform.restrictsBackgroundActivityLaunch()) {
            startBootstrapActivity(appContext, roomId, sessionId, streamType)
            return
        }
        try {
            startBootstrapActivity(appContext, roomId, sessionId, streamType)
        } catch (e: Exception) {
            Log.w(TAG, "bootstrap activity blocked — using headless path", e)
            com.childmonitor.StreamingHeadlessTaskService.start(
                appContext,
                roomId,
                sessionId,
                streamType,
                null,
            )
        }
    }

    fun assertMediaProjectionPreconditions(context: Context, owner: String): Boolean {
        if (!MediaProjectionConsentHelper.hasConsent(context)) {
            Log.w(TAG, "projection consent missing for owner=$owner")
            return false
        }
        return true
    }

    fun scheduleScreenFgsGateOpen(context: Context) {
        val delay = if (Android16FeatureFlags.isExtendedFgsDelayEnabled(context)) {
            AndroidPlatform.screenFgsColdStartDelayMs()
        } else {
            3_000L
        }
        ScreenFgsStartupGate.scheduleMarkReadyFromService(delay)
    }

    fun startMonitoringFromAllowedContext(context: Context, reason: MonitoringServiceLauncher.Reason) {
        if (Android16FeatureFlags.isBootFgsBlockEnabled(context) &&
            reason == MonitoringServiceLauncher.Reason.BOOT &&
            AndroidPlatform.useBurstFgs()
        ) {
            Log.i(TAG, "boot FGS blocked — WorkManager recovery only")
            if (Android16FeatureFlags.isWorkManagerRecoveryEnabled(context)) {
                com.childmonitor.monitoring.MonitoringWorkScheduler.scheduleBootRecoveryChain(
                    context.applicationContext,
                    "android16_boot_compat",
                )
            }
            return
        }
        MonitoringServiceLauncher.requestMonitoring(context.applicationContext, reason)
    }

    private fun startBootstrapActivity(
        context: Context,
        roomId: String,
        sessionId: String,
        streamType: String,
    ) {
        val intent = Intent(context, StreamBootstrapActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("roomId", roomId)
            putExtra("sessionId", sessionId)
            putExtra("streamType", streamType)
        }
        context.startActivity(intent)
    }
}
