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
import com.childmonitor.security.DeviceTrustSignalsCollector
import com.childmonitor.telemetry.MobileTelemetryReporter
import com.childmonitor.TamperProtectionManager
import org.json.JSONObject
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
        val accessibilityServiceConnected = accessibilityServiceConnectedOverride
            ?: (AppBlockingAccessibilityService.instance != null)
        val telemetry = DeviceTelemetryReader.read(context)
        val foregroundApp = ForegroundAppTracker.current()
        val fcmToken = FcmTokenRegistrar.tokenForHeartbeat(context)
        val integrityToken = MonitoringPrefs.cachedIntegrityToken(context, "heartbeat")
        val trustSignals = DeviceTrustSignalsCollector.collect(context)
        val oemDiagnostics = OemDiagnosticsCollector.collect(context)
        val readinessScore = OemSetupValidator.computeReadinessScore(context)

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
            .apply {
                if (foregroundApp != null) put("foregroundApp", foregroundApp)
                if (!fcmToken.isNullOrBlank()) put("fcmToken", fcmToken)
                if (!integrityToken.isNullOrBlank()) put("integrityToken", integrityToken)
                put("trustSignals", JSONObject(DeviceTrustSignalsCollector.toJsonMap(trustSignals)))
                put("oemVendor", oemDiagnostics["vendor"])
                put("androidSdkInt", oemDiagnostics["sdkInt"])
                put("deviceReadinessScore", readinessScore)
                put("android16ReadinessScore", oemDiagnostics["android16ReadinessScore"])
                put("android16Blockers", oemDiagnostics["android16Blockers"])
                put("platformSdkTarget", AndroidPlatform.SDK_ANDROID_16)
                put("oemSetupComplete", oemDiagnostics["oemSetupComplete"])
                put("oemDiagnostics", OemDiagnosticsCollector.toJson(context))
            }
    }

    fun postHeartbeat(context: Context, body: JSONObject): Boolean =
        DeviceHttpClient.postJson(context, ApiPaths.DEVICE_HEARTBEAT, body.toString())

    fun syncNow(context: Context): Boolean {
        if (!MonitoringPrefs.isPaired(context)) return false
        val body = buildHeartbeatBody(context)
        val ok = postHeartbeat(context, body)
        MobileTelemetryReporter.flushIfDue(context)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "heartbeat sync",
            "ok=$ok consentState=${body.optString("mediaProjectionConsentState")} " +
                "granted=${body.optBoolean("mediaProjectionGranted")}",
        )
        return ok
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
}
