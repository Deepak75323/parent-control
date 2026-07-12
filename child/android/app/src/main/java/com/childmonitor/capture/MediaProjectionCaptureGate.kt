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

    @Synchronized
    fun release(owner: String) {
        if (activeOwner == owner) {
            activeOwner = null
            CaptureFrameLogger.log("CAPTURE_GATE_RELEASED", owner)
        }
    }

    @Synchronized
    fun isHeld(): Boolean = activeOwner != null
}
