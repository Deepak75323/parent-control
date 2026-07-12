package com.childmonitor.capture

import android.content.Context
import com.childmonitor.telemetry.StreamLifecycleTelemetry

/**
 * P1-7: Periodic orphan session cleanup when registry diverges from runtime.
 */
object StreamSessionWatchdog {

    private const val ORPHAN_AGE_MS = 120_000L

    fun tick(context: Context) {
        val snap = StreamSessionRegistry.snapshot()
        val ageMs = (snap["ageMs"] as? Long) ?: 0L
        val nativeRunning = NativeStreamPublisher.isRunning()
        val owner = StreamSessionRegistry.activeOwner()

        if (owner == StreamSessionRegistry.Owner.NATIVE_PUBLISHER && !nativeRunning) {
            StreamLifecycleTelemetry.record("watchdog_orphan_native", snap)
            StreamSessionRegistry.release(owner)
            NativeStreamPublisher.stop(context)
            return
        }

        if (owner != StreamSessionRegistry.Owner.NONE &&
            ageMs > ORPHAN_AGE_MS &&
            snap["phase"] == StreamSessionRegistry.LifecyclePhase.ACQUIRING.name
        ) {
            StreamLifecycleTelemetry.record("watchdog_stuck_acquiring", snap)
            StreamSessionRegistry.release(owner)
        }
    }
}
