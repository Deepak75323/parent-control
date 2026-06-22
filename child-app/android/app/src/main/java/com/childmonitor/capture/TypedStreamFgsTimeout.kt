package com.childmonitor.capture

import android.app.Service
import android.content.Context
import android.os.Build
import android.util.Log
import com.childmonitor.monitoring.MonitoringPlatform
import com.childmonitor.sync.DeviceHttpClient

/**
 * Graceful shutdown for typed capture foreground services on Android 15+.
 * Invoked from [Service.onTimeout] when the system limits long-running FGS.
 */
object TypedStreamFgsTimeout {

    private const val TAG = "TypedStreamFgsTimeout"

    fun handle(
        service: Service,
        tag: String,
        kind: StreamKind,
        onBeforeStop: () -> Unit = {},
    ) {
        if (!MonitoringPlatform.requiresFgsTimeoutHandler()) {
            return
        }
        Log.w(tag, "typed FGS timeout — stopping $kind gracefully")
        // P1-4: Notify backend so the parent UI shows a clear "session ended by Android"
        // message and can re-request the stream, instead of staring at a frozen viewport.
        val sessionId = StreamSessionRegistry.activeSessionId()
        if (sessionId != null) {
            reportTimeoutAsync(service.applicationContext, sessionId, kind)
        } else {
            Log.i(tag, "FGS timeout for $kind — no active session to report")
        }
        onBeforeStop()
        StreamCaptureTeardown.stopKind(service.applicationContext, kind)
        try {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        service.stopSelf()
    }

    private fun reportTimeoutAsync(context: Context, sessionId: String, kind: StreamKind) {
        Thread {
            try {
                DeviceHttpClient.reportStreamFailure(
                    context,
                    sessionId,
                    "FGS_TIMEOUT_${kind.name}",
                )
            } catch (e: Exception) {
                Log.w(TAG, "reportStreamFailure failed after FGS timeout", e)
            }
        }.start()
    }
}
