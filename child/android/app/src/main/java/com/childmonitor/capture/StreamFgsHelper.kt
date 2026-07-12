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
}
