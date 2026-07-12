package com.childmonitor.capture

import android.app.Service
import android.content.Context
import android.os.Build
import android.util.Log
import com.childmonitor.monitoring.MonitoringPlatform
import com.childmonitor.sync.DeviceHttpClient
import com.childmonitor.sync.NativeStreamingCoordinator

/**
 * Graceful shutdown for typed capture foreground services on Android 15+.
 * Invoked from [Service.onTimeout] when the system limits long-running FGS.
 */
object TypedStreamFgsTimeout {

    private const val TAG = "TypedStreamFgsTimeout"

    /**
     * Max time (ms) to block waiting for the backend report before we must call stopSelf().
     * 5 s is generous; the OS grants ~10 s for onTimeout cleanup before ANR.
     */
    private const val REPORT_JOIN_TIMEOUT_MS = 5_000L

    fun handle(
        service: Service,
        tag: String,
        kind: StreamKind,
        onBeforeStop: () -> Unit = {},
    ) {
        if (!MonitoringPlatform.requiresFgsTimeoutHandler()) {
            return
        }
        Log.w(tag, "typed FGS timeout — stopping $kind gracefully (API ${Build.VERSION.SDK_INT})")

        // P1-4: Resolve session ID from the registry first, then fall back to the
        // coordinator's tracking field.  The registry may be empty when the stream
        // was started via the headless task-service path (which doesn't call
        // StreamSessionRegistry.tryAcquire), so the fallback prevents a silent gap
        // where the backend never learns that the stream ended.
        val sessionId: String? =
            StreamSessionRegistry.activeSessionId()
                ?: NativeStreamingCoordinator.activeSessionId()

        Log.i(tag, "FGS timeout $kind sessionId=${sessionId ?: "unknown"} " +
            "registrySession=${StreamSessionRegistry.activeSessionId()} " +
            "coordinatorSession=${NativeStreamingCoordinator.activeSessionId()}")

        onBeforeStop()
        // Stop the capture pipeline before the HTTP call so the server-side timer
        // is not racing with a still-running encoder.
        StreamCaptureTeardown.stopKind(service.applicationContext, kind, "fgs_timeout_${kind.name.lowercase()}")

        // P1-4: Block briefly on the HTTP report so it completes before the process loses
        // its foreground scheduling priority.  The previous async fire-and-forget was
        // frequently cancelled mid-flight when the OS aggressively backgrounded the app
        // after stopSelf(), leaving the parent staring at a frozen viewport indefinitely.
        if (sessionId != null) {
            reportTimeoutBlocking(service.applicationContext, sessionId, kind)
        } else {
            Log.w(TAG, "FGS timeout for $kind — no active session to report to backend")
        }

        try {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        service.stopSelf()
    }

    /**
     * Fires the failure report on a background thread then blocks up to [REPORT_JOIN_TIMEOUT_MS]
     * before returning.  This guarantees the HTTP call completes before [Service.stopSelf] runs,
     * without risking an ANR if the network is unavailable.
     */
    private fun reportTimeoutBlocking(context: Context, sessionId: String, kind: StreamKind) {
        val t = Thread {
            try {
                DeviceHttpClient.reportStreamFailure(
                    context,
                    sessionId,
                    "FGS_TIMEOUT_${kind.name}",
                )
                Log.i(TAG, "FGS timeout reported to backend kind=$kind session=$sessionId")
            } catch (e: Exception) {
                Log.w(TAG, "reportStreamFailure failed after FGS timeout kind=$kind session=$sessionId", e)
            }
        }
        t.start()
        try {
            t.join(REPORT_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "FGS timeout report join interrupted kind=$kind session=$sessionId")
        }
        if (t.isAlive) {
            Log.w(TAG, "FGS timeout report did not complete within ${REPORT_JOIN_TIMEOUT_MS}ms — proceeding with stop")
        }
    }
}
