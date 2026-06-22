package com.childmonitor.capture

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.oney.WebRTCModule.HiddenCameraBridge

/**
 * Kotlin wrapper around the package-local WebRTC bridge for hidden camera capture.
 */
object HiddenCameraCapture {

    fun createMediaStream(
        reactContext: ReactApplicationContext,
        facingMode: String,
        promise: Promise,
    ) {
        HiddenCameraBridge.createCameraStream(
            reactContext,
            facingMode,
            promise,
            HiddenCameraBridge.ForegroundGate {
                StreamFgsBootstrap.ensureActive(reactContext, StreamKind.CAMERA)
            },
            Runnable { CameraStreamForegroundService.stop(reactContext) },
        )
    }

    fun stop(reactContext: ReactApplicationContext) {
        stopCaptureOnly(reactContext)
        CameraStreamForegroundService.stopSafely(reactContext, "hidden-camera-stop")
    }

    fun stopCaptureOnly(reactContext: ReactApplicationContext) {
        HiddenCameraBridge.stopActiveCapture(reactContext)
    }
}
