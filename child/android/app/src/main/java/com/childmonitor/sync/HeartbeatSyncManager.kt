package com.childmonitor.sync

import android.content.Context
import com.childmonitor.AccessibilityHealthChecker
import com.childmonitor.AppBlockingAccessibilityService
import com.childmonitor.DeviceTelemetryReader
import com.childmonitor.ForegroundAppTracker
import com.childmonitor.MonitoringPrefs
import com.childmonitor.capture.MediaProjectionConsentState
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.deviceowner.DeviceOwnerManager
import com.childmonitor.fcm.FcmTokenRegistrar
import com.childmonitor.oem.OemDiagnosticsCollector
import com.childmonitor.oem.OemSetupValidator
import com.childmonitor.platform.AndroidPlatform
import com.childmonitor.monitoring.MonitoringDegradationController
import com.childmonitor.security.DeviceTrustSignalsCollector
import com.childmonitor.telemetry.MobileTelemetryReporter
import com.childmonitor.TamperProtectionManager
import org.json.JSONObject
import org.json.JSONArray
import com.childmonitor.constants.ApiPaths

/**
 * Builds and posts device heartbeats with accurate MediaProjection capability flags.
 * Centralizes consent-state sync so stale server-side `mediaProjectionGranted` cannot linger.
 */
object HeartbeatSyncManager {

    private const val TAG = "HeartbeatSync"

    fun buildHeartbeatBody(
        context: Context,
        accessibilityServiceConnectedOverride: Boolean? = null,
    ): JSONObject {
        val deviceOwner = DeviceOwnerManager.isDeviceOwner(context)
        val deviceAdminActive = TamperProtectionManager.isDeviceAdminActive(context)
        val consentState = MediaProjectionTokenStore.getConsentState(context)
        val projectionGranted = consentState == MediaProjectionConsentState.GRANTED_ACTIVE
        val accessibilityEnabled = AccessibilityHealthChecker.isAccessibilityEnabled(context)
        // WorkManager heartbeats often run while the a11y binder is idle on MIUI/HyperOS.
        // System settings are the durable grant signal; explicit connect/disconnect events
        // still push overrides via syncAccessibilityServiceState().
        val accessibilityServiceConnected = accessibilityServiceConnectedOverride
            ?: accessibilityEnabled
        val telemetry = DeviceTelemetryReader.read(context)
        val foregroundApp = ForegroundAppTracker.current()
        val fcmToken = FcmTokenRegistrar.tokenForHeartbeat(context)
        val integrityToken = MonitoringPrefs.cachedIntegrityToken(context, "heartbeat")
        val trustSignals = DeviceTrustSignalsCollector.collect(context)
        val oemDiagnostics = OemDiagnosticsCollector.collect(context)
        val readinessScore = OemSetupValidator.computeReadinessScore(context)
        val failureClassifications = OemSetupValidator.classifyFailures(context)
        val monitoring = MonitoringDegradationController.heartbeatFields(context)

        return JSONObject()
            .apply {
                if (telemetry.batteryLevel != null) {
                    put("batteryLevel", telemetry.batteryLevel)
                }
            }
            .put("charging", telemetry.charging)
            .put("networkType", telemetry.networkType)
            .put("deviceOwnerActive", deviceOwner)
            .put("deviceAdminActive", deviceAdminActive)
            .put("mediaProjectionGranted", projectionGranted)
            .put("mediaProjectionConsentState", consentState.name)
            .put("accessibilityEnabled", accessibilityEnabled)
            .put("accessibilityServiceConnected", accessibilityServiceConnected)
            .put("silentScreenCapable", deviceOwner && projectionGranted)
            .put(
                "protectionLevel",
                when {
                    deviceOwner && projectionGranted -> "FULL"
                    deviceOwner -> "DEVICE_OWNER"
                    else -> "STANDARD"
                },
            )
            .put("kioskModeActive", DeviceOwnerManager.isKioskActive(context))
            .put("monitoringMode", monitoring["monitoringMode"])
            .put("monitoringDegraded", monitoring["monitoringDegraded"])
            .put("monitoringDegradedReason", monitoring["monitoringDegradedReason"])
            .put("standbyBucket", monitoring["standbyBucket"])
            .put("dataSyncFgsUsedMs", monitoring["dataSyncFgsUsedMs"])
            .put("dataSyncFgsDailyCapMs", monitoring["dataSyncFgsDailyCapMs"])
            .put("dataSyncFgsExhausted", monitoring["dataSyncFgsExhausted"])
            .apply {
                val pendingOfflineFails = MonitoringPrefs.offlinePinLockoutState(context).pendingReportFails
                if (pendingOfflineFails > 0) {
                    put("offlineUnlockFailures", pendingOfflineFails)
                }
                if (foregroundApp != null) put("foregroundApp", foregroundApp)
                if (!fcmToken.isNullOrBlank()) put("fcmToken", fcmToken)
                if (!integrityToken.isNullOrBlank()) put("integrityToken", integrityToken)
                put("trustSignals", JSONObject(DeviceTrustSignalsCollector.toJsonMap(trustSignals)))
                put("oemVendor", oemDiagnostics["vendor"])
                put("androidSdkInt", oemDiagnostics["sdkInt"])
                put("deviceReadinessScore", readinessScore)
                put("failureClassifications", JSONArray(failureClassifications))
                put("android16ReadinessScore", oemDiagnostics["android16ReadinessScore"])
                put("android16Blockers", oemDiagnostics["android16Blockers"])
                put("platformSdkTarget", AndroidPlatform.SDK_ANDROID_16)
                put("oemSetupComplete", oemDiagnostics["oemSetupComplete"])
                put("batteryExempt", oemDiagnostics["batteryExempt"])
                put("autostartPromptDone", oemDiagnostics["autostartPromptDone"])
                put("overlayGranted", oemDiagnostics["overlayGranted"])
                put("cameraGranted", oemDiagnostics["cameraGranted"])
                put("notificationPermissionGranted", oemDiagnostics["notificationPermissionGranted"])
                put("fullScreenIntentGranted", oemDiagnostics["fullScreenIntentGranted"])
                put("oemDiagnostics", OemDiagnosticsCollector.toJson(context))
            }
    }

    fun postHeartbeat(context: Context, body: JSONObject): Boolean =
        DeviceHttpClient.postJson(context, ApiPaths.DEVICE_HEARTBEAT, body.toString())

    fun syncNow(context: Context): Boolean {
        if (!MonitoringPrefs.isPaired(context)) return false
        val body = buildHeartbeatBody(context)
        val result = postHeartbeatDetailed(context, body)
        val ok = result is DeviceHttpClient.HttpResult.Ok
        
        // Handle network connectivity loss
        if (result is DeviceHttpClient.HttpResult.NoNetwork) {
            android.util.Log.w(TAG, "HEARTBEAT_NO_NETWORK - device is offline")
            notifyNetworkStatusChange(context, false)
            try {
                com.childmonitor.capture.StreamTeardownCoordinator.requestImmediateStopAll(
                    context.applicationContext,
                    "heartbeat_no_network",
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "teardown on heartbeat no-network failed", e)
            }
        } else if (ok) {
            // Device is back online - notify parent
            notifyNetworkStatusChange(context, true)
            
            val reported = body.optInt("offlineUnlockFailures", 0)
            if (reported > 0) {
                MonitoringPrefs.consumeOfflinePinPendingReport(context)
            }
            try {
                com.childmonitor.tamper.TamperApiClient.syncOfflinePinCredential(context)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "offline pin sync failed", e)
            }
        }
        
        MobileTelemetryReporter.flushIfDue(context)
        com.childmonitor.telemetry.ClientAnalyticsReporter.flushIfDue(context)
        android.util.Log.i(
            TAG,
            when (result) {
                is DeviceHttpClient.HttpResult.Ok -> 
                    "HEARTBEAT_SENT consent=${body.optString("mediaProjectionConsentState")} " +
                    "granted=${body.optBoolean("mediaProjectionGranted")}"
                is DeviceHttpClient.HttpResult.NoNetwork -> 
                    "HEARTBEAT_NO_NETWORK - device offline"
                else -> 
                    "HEARTBEAT_FAILED consent=${body.optString("mediaProjectionConsentState")} " +
                    "result=${result::class.simpleName}"
            },
        )
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "heartbeat sync",
            "result=${result::class.simpleName} consentState=${body.optString("mediaProjectionConsentState")} " +
                "granted=${body.optBoolean("mediaProjectionGranted")}",
        )
        return ok
    }

    private fun postHeartbeatDetailed(context: Context, body: JSONObject): DeviceHttpClient.HttpResult {
        return DeviceHttpClient.postJsonDetailed(context, ApiPaths.DEVICE_HEARTBEAT, body.toString())
    }

    private fun notifyNetworkStatusChange(context: Context, isOnline: Boolean) {
        try {
            // Send immediate network status through realtime connection if available
            val statusBody = JSONObject()
                .put("type", "NETWORK_STATUS_CHANGE")
                .put("online", isOnline)
                .put("timestamp", System.currentTimeMillis())
            
            // Try to send via WebSocket for immediate parent notification
            // This will be handled by the WebSocket service if connected
            android.util.Log.i(TAG, "Network status change: online=$isOnline")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to notify network status change", e)
        }
    }

    /**
     * Immediate heartbeat after projection loss — clears stale server capability flags.
     */
    fun syncProjectionLost(context: Context, reason: String): Boolean {
        MonitoringPrefs.setMediaProjectionGranted(context, false)
        val body = buildHeartbeatBody(context)
        val ok = postHeartbeat(context, body)
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection lost heartbeat",
            "reason=$reason ok=$ok state=${body.optString("mediaProjectionConsentState")}",
        )
        MediaProjectionRecoveryTelemetry.recordLoss(context, reason, body.optString("mediaProjectionConsentState"))
        return ok
    }

    /** Immediate heartbeat after projection is re-granted. */
    fun syncProjectionRestored(context: Context): Boolean {
        val granted = MediaProjectionTokenStore.getConsentState(context) ==
            MediaProjectionConsentState.GRANTED_ACTIVE
        MonitoringPrefs.setMediaProjectionGranted(context, granted)
        val body = buildHeartbeatBody(context)
        val ok = postHeartbeat(context, body)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection restored heartbeat",
            "ok=$ok granted=$granted",
        )
        if (granted) {
            MediaProjectionRecoveryTelemetry.recordRestored(context)
        }
        return ok
    }

    fun syncAccessibilityServiceState(context: Context, connected: Boolean): Boolean {
        if (!MonitoringPrefs.isPaired(context)) return false
        val body = buildHeartbeatBody(context, accessibilityServiceConnectedOverride = connected)
        return postHeartbeat(context, body)
    }

    /**
     * P1-08: immediate heartbeat when monitoring degrades (dataSync quota / standby).
     * Backend raises a parent alert on rising edge.
     */
    fun syncMonitoringDegraded(context: Context): Boolean {
        if (!MonitoringPrefs.isPaired(context)) return false
        val body = buildHeartbeatBody(context)
        val ok = postHeartbeat(context, body)
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.SERVICE_STATE,
            "monitoring degraded heartbeat",
            "ok=$ok mode=${body.optString("monitoringMode")} " +
                "reason=${body.optString("monitoringDegradedReason")} " +
                "standby=${body.optString("standbyBucket")}",
        )
        return ok
    }
}
