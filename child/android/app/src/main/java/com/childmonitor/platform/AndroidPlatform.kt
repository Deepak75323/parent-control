package com.childmonitor.platform

import android.content.Context
import android.os.Build
import com.childmonitor.monitoring.MonitoringWorkScheduler

/**
 * Central Android API capability detection for SDK 24–36+.
 * All version-gated behavior should route through this object (or [Android16CompatibilityLayer]).
 */
object AndroidPlatform {

    /** Android 16 (API 36). Use numeric constant until BAKLAVA is stable in compile SDK. */
    const val SDK_ANDROID_16 = 36

    fun sdkInt(): Int = Build.VERSION.SDK_INT

    fun isAndroid16OrLater(): Boolean = sdkInt() >= SDK_ANDROID_16

    fun isAndroid15OrLater(): Boolean =
        sdkInt() >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    /** Android 15+ — short dataSync FGS bursts instead of 24/7 monitoring FGS. */
    fun useBurstFgs(): Boolean = isAndroid15OrLater()

    /** Android 15+ — [android.app.Service.onTimeout] for typed/dataSync FGS. */
    fun requiresFgsTimeoutHandler(): Boolean = isAndroid15OrLater()

    /**
     * Android 16 enforces single VirtualDisplay per MediaProjection instance.
     * [com.childmonitor.capture.MediaProjectionCaptureGate] must be held before capture.
     */
    fun requiresMediaProjectionCaptureGate(): Boolean = isAndroid16OrLater()

    /** Background activity starts are increasingly restricted from Android 10+; stricter on 16. */
    fun restrictsBackgroundActivityLaunch(): Boolean =
        sdkInt() >= Build.VERSION_CODES.Q

    /** Edge-to-edge is mandatory for apps targeting API 36. */
    fun enforcesEdgeToEdge(): Boolean = isAndroid16OrLater()

    /** dataSync FGS daily cap (hours) when targeting API 35+. */
    fun dataSyncFgsDailyCapHours(): Int = 6

    /** Media projection FGS cannot start from BOOT_COMPLETED when targeting API 35+. */
    fun blocksMediaProjectionFgsFromBoot(): Boolean = isAndroid15OrLater()

    /** Camera/mic FGS require runtime permissions before startForeground (API 34+). */
    fun requiresRuntimePermissionBeforeTypedFgs(): Boolean =
        sdkInt() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Recommended delay before screen FGS after cold start (ms) — higher on API 36. */
    fun screenFgsColdStartDelayMs(): Long = when {
        isAndroid16OrLater() -> 10_000L
        isAndroid15OrLater() -> 8_000L
        else -> 3_000L
    }

    fun capabilities(context: Context): Map<String, Any> = linkedMapOf(
        "sdkInt" to sdkInt(),
        "osRelease" to (Build.VERSION.RELEASE ?: "unknown"),
        "targetAndroid16" to isAndroid16OrLater(),
        "useBurstFgs" to useBurstFgs(),
        "requiresFgsTimeoutHandler" to requiresFgsTimeoutHandler(),
        "requiresMediaProjectionCaptureGate" to requiresMediaProjectionCaptureGate(),
        "restrictsBackgroundActivityLaunch" to restrictsBackgroundActivityLaunch(),
        "enforcesEdgeToEdge" to enforcesEdgeToEdge(),
        "blocksMediaProjectionFgsFromBoot" to blocksMediaProjectionFgsFromBoot(),
        "dataSyncFgsDailyCapHours" to dataSyncFgsDailyCapHours(),
        "screenFgsColdStartDelayMs" to screenFgsColdStartDelayMs(),
        "workManagerRecovery" to MonitoringWorkScheduler.isScheduled(context),
        "android16FeatureFlags" to Android16FeatureFlags.snapshot(context),
        "monitoringDegradation" to
            com.childmonitor.monitoring.MonitoringDegradationController.heartbeatFields(context),
    )
}
