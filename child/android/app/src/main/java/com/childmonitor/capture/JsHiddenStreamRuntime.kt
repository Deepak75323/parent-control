package com.childmonitor.capture

import com.oney.WebRTCModule.HiddenAudioBridge
import com.oney.WebRTCModule.HiddenCameraBridge

/**
 * Runtime signals for the JS hidden stream path (screen / camera / audio).
 *
 * Used by orphan reconciliation — must reflect live capture/FGS, not merely that React
 * can receive DeviceEventEmitter events (which stays true after teardown).
 */
object JsHiddenStreamRuntime {

    @JvmStatic
    fun isCapturePipelineLive(): Boolean {
        if (StreamSessionRegistry.activeOwner() != StreamSessionRegistry.Owner.JS_HIDDEN) {
            return false
        }
        return ScreenCaptureForegroundService.isForegroundActive() ||
            CameraStreamForegroundService.isActive() ||
            AudioStreamForegroundService.isActive() ||
            HiddenCameraBridge.isCaptureActive() ||
            HiddenAudioBridge.isCaptureActive()
    }
}
