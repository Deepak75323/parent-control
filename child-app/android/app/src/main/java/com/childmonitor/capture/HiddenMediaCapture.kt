package com.childmonitor.capture

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.oney.WebRTCModule.HiddenAudioBridge
import com.oney.WebRTCModule.HiddenScreenBridge

object HiddenAudioCapture {

    fun createMediaStream(reactContext: ReactApplicationContext, promise: Promise) {
        HiddenAudioBridge.createAudioStream(
            reactContext,
            promise,
            HiddenAudioBridge.ForegroundGate {
                StreamFgsBootstrap.ensureActive(reactContext, StreamKind.AUDIO)
            },
            Runnable { AudioStreamForegroundService.stop(reactContext) },
        )
    }

    fun stop(reactContext: ReactApplicationContext) {
        stopCaptureOnly(reactContext)
        AudioStreamForegroundService.stopSafely(reactContext, "hidden-audio-stop")
    }

    fun stopCaptureOnly(reactContext: ReactApplicationContext) {
        HiddenAudioBridge.stopActiveCapture(reactContext)
    }
}

object HiddenScreenCapture {

    fun createMediaStream(reactContext: ReactApplicationContext, promise: Promise) {
        HiddenScreenBridge.createScreenStream(
            reactContext,
            promise,
            HiddenScreenBridge.ForegroundGate {
                StreamFgsBootstrap.ensureActive(reactContext, StreamKind.SCREEN)
            },
            // P1-1: when the JS-owned video track ends (parent stopped viewing, capture lost,
            // or RN released the stream), tear down the screen FGS too. Otherwise the
            // mediaProjection FGS lingered until process death, holding the projection
            // session and the silent notification.
            Runnable { ScreenCaptureForegroundService.stopStream(reactContext) },
        )
    }

    fun stop(reactContext: ReactApplicationContext) {
        HiddenScreenBridge.stopActiveCapture(reactContext)
        ScreenCaptureForegroundService.stopStream(reactContext)
    }
}
