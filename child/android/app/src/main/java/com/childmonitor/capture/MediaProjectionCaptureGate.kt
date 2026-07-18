package com.childmonitor.capture

import android.content.Context
import com.childmonitor.platform.Android16FeatureFlags
import com.childmonitor.platform.AndroidPlatform

/**
 * Ensures only one [VirtualDisplay] exists per process for the shared MediaProjection.
 * Android 16 throws SecurityException on a second createVirtualDisplay for the same instance.
 *
 * P2-09: Honors [Android16FeatureFlags.isStrictProjectionGateEnabled] when a [Context] is supplied.
 */
object MediaProjectionCaptureGate {

    @Volatile
    private var activeOwner: String? = null

    @Synchronized
    @JvmOverloads
    fun tryAcquire(owner: String, context: Context? = null): Boolean {
        if (context != null &&
            AndroidPlatform.isAndroid16OrLater() &&
            !Android16FeatureFlags.isStrictProjectionGateEnabled(context)
        ) {
            CaptureFrameLogger.log("CAPTURE_GATE_BYPASS", "flag=strict_projection_gate off owner=$owner")
            activeOwner = owner
            return true
        }
        if (activeOwner != null && activeOwner != owner) {
            CaptureFrameLogger.warn(
                "CAPTURE_GATE_BLOCKED",
                "owner=$owner activeOwner=$activeOwner",
            )
            return false
        }
        activeOwner = owner
        CaptureFrameLogger.log("CAPTURE_GATE_ACQUIRED", owner)
        return true
    }

    /**
     * Takes the gate even if another owner holds it. Used when a previous capturer
     * tore down incompletely (VirtualDisplay already detached) and left a stuck owner.
     */
    @Synchronized
    fun forceAcquire(owner: String, reason: String): Boolean {
        val prev = activeOwner
        if (prev != null && prev != owner) {
            CaptureFrameLogger.warn(
                "CAPTURE_GATE_FORCE_STEAL",
                "from=$prev to=$owner reason=$reason",
            )
        }
        activeOwner = owner
        CaptureFrameLogger.log("CAPTURE_GATE_ACQUIRED", "$owner (forced)")
        return true
    }

    @Synchronized
    fun release(owner: String) {
        if (activeOwner == owner) {
            activeOwner = null
            CaptureFrameLogger.log("CAPTURE_GATE_RELEASED", owner)
        }
    }

    /** Clears any holder — used on intentional full teardown. */
    @Synchronized
    fun forceRelease(reason: String) {
        val prev = activeOwner ?: return
        activeOwner = null
        CaptureFrameLogger.warn("CAPTURE_GATE_FORCE_RELEASE", "owner=$prev reason=$reason")
    }

    @Synchronized
    fun isHeld(): Boolean = activeOwner != null

    @Synchronized
    fun activeOwner(): String? = activeOwner
}
