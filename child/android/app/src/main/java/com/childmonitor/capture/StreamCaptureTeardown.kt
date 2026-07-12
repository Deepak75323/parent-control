package com.childmonitor.capture

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Idempotent teardown entry point — delegates ordering to [StreamTeardownCoordinator].
 */
object StreamCaptureTeardown {

    private const val TAG = "StreamCaptureTeardown"

    @JvmField
    @Volatile
    var lastReason: String? = null

    private val invocationCount = AtomicInteger(0)

    @JvmStatic
    fun invocationCount(): Int = invocationCount.get()

    @JvmStatic
    fun stopAll(context: Context, reason: String) {
        invocationCount.incrementAndGet()
        lastReason = reason
        StreamLifecycleLogger.warn("STREAM_CAPTURE_TEARDOWN", reason)
        Log.i(TAG, "stopAll reason=$reason → coordinator")
        StreamTeardownCoordinator.requestStopAll(context.applicationContext, reason)
    }

    /** Stop a single typed capture FGS and its pipeline (used from FGS onTimeout). */
    @JvmStatic
    fun stopKind(context: Context, kind: StreamKind, reason: String = "fgs_timeout") {
        Log.i(TAG, "stopKind kind=$kind reason=$reason → coordinator")
        StreamTeardownCoordinator.requestStopKind(context.applicationContext, kind, reason)
    }
}
