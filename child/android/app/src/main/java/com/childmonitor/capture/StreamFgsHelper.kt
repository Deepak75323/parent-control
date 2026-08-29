package com.childmonitor.capture

import android.content.Context

/** Routes FGS start/wait/isActive calls to the correct stream service. */
object StreamFgsHelper {

    @JvmStatic
    fun isActive(kind: StreamKind): Boolean = when (kind) {
        StreamKind.CAMERA -> CameraStreamForegroundService.isActive()
        StreamKind.AUDIO -> AudioStreamForegroundService.isActive()
        StreamKind.SCREEN -> ScreenCaptureForegroundService.isForegroundActive()
    }

    @JvmStatic
    fun startAndWait(context: Context, kind: StreamKind, timeoutMs: Long = 8000L): Boolean =
        when (kind) {
            StreamKind.CAMERA -> CameraStreamForegroundService.startAndWait(context, timeoutMs)
            StreamKind.AUDIO -> AudioStreamForegroundService.startAndWait(context, timeoutMs)
            StreamKind.SCREEN -> ScreenCaptureForegroundService.startAndWait(context, timeoutMs)
        }

    /**
     * Non-blocking FGS intent while a visible trampoline activity is resumed.
     * Screen capture has no simple start path — use [startAndWait] on a worker thread.
     */
    @JvmStatic
    fun startServiceIntent(context: Context, kind: StreamKind) {
        when (kind) {
            StreamKind.CAMERA -> CameraStreamForegroundService.start(context)
            StreamKind.AUDIO -> AudioStreamForegroundService.start(context)
            StreamKind.SCREEN -> Unit
        }
    }
}
