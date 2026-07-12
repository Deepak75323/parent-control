package com.childmonitor.capture

/**
 * Ensures only one [VirtualDisplay] exists per process for the shared MediaProjection.
 * Android 16 throws SecurityException on a second createVirtualDisplay for the same instance.
 */
object MediaProjectionCaptureGate {

    @Volatile
    private var activeOwner: String? = null

    @Synchronized
    fun tryAcquire(owner: String): Boolean {
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
