package com.parentscaffold.native

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.UIManagerModule
import com.parentscaffold.BuildConfig
import com.parentscaffold.capture.StreamRecorder
import com.parentscaffold.security.ParentSecureAuthStore

class ParentNativeModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusListener = OnAudioFocusChangeListener { /* listen-only */ }

    override fun getName(): String = "ParentNative"

    override fun getConstants(): MutableMap<String, Any> =
        hashMapOf("mapsAvailable" to BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank())

    /**
     * Android RTCView uses [SurfaceView], which ignores parent RN transform styles.
     * Apply rotation/scale directly on the SurfaceView (and host) so viewer rotate/zoom work.
     */
    @ReactMethod
    fun applyViewerVideoTransform(reactTag: Int, rotationDeg: Double, scale: Double, promise: Promise) {
        UiThreadUtil.runOnUiThread {
            try {
                val uiManager = reactContext.getNativeModule(UIManagerModule::class.java)
                if (uiManager == null) {
                    promise.reject("NO_UIMANAGER", "UIManagerModule unavailable")
                    return@runOnUiThread
                }
                val view = uiManager.resolveView(reactTag)
                if (view == null) {
                    promise.reject("NO_VIEW", "No view for reactTag=$reactTag")
                    return@runOnUiThread
                }
                val rot = rotationDeg.toFloat()
                val sc = scale.toFloat().coerceIn(0.25f, 8f)
                applyViewerTransformRecursive(view, rot, sc)
                // Layout may still be 0×0 on first attach — re-apply after next layout.
                if (view.width == 0 || view.height == 0) {
                    view.post {
                        applyViewerTransformRecursive(view, rot, sc)
                    }
                }
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("VIEWER_TRANSFORM", e)
            }
        }
    }

    private fun applyViewerTransformRecursive(view: View, rotation: Float, scale: Float) {
        val applyHere =
            view is SurfaceView ||
                view.javaClass.name.contains("SurfaceView", ignoreCase = true) ||
                view.javaClass.name.contains("WebRTCView", ignoreCase = true)
        if (applyHere) {
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            if (w > 0f && h > 0f) {
                view.pivotX = w / 2f
                view.pivotY = h / 2f
            }
            view.rotation = rotation
            view.scaleX = scale
            view.scaleY = scale
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyViewerTransformRecursive(view.getChildAt(i), rotation, scale)
            }
        }
    }

    @ReactMethod
    fun saveSecureAuth(json: String, promise: Promise) {
        try {
            ParentSecureAuthStore.saveAuthJson(reactContext, json)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SECURE_AUTH_SAVE", e)
        }
    }

    @ReactMethod
    fun loadSecureAuth(promise: Promise) {
        try {
            promise.resolve(ParentSecureAuthStore.loadAuthJson(reactContext))
        } catch (e: Exception) {
            promise.reject("SECURE_AUTH_LOAD", e)
        }
    }

    @ReactMethod
    fun clearSecureAuth(promise: Promise) {
        try {
            ParentSecureAuthStore.clear(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SECURE_AUTH_CLEAR", e)
        }
    }

    @ReactMethod
    fun setViewerAudioMode(enabled: Boolean, promise: Promise) {
        try {
            val audioManager =
                reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (enabled) {
                abandonViewerAudioFocus(audioManager)
                // IN_COMMUNICATION activates hardware acoustic echo cancellation on the parent
                // device so that any ambient sound picked up by the parent microphone doesn't
                // loop back through the child's WebRTC receive path.
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                boostMusicVolume(audioManager)
                requestViewerAudioFocus(audioManager)
            } else {
                abandonViewerAudioFocus(audioManager)
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("AUDIO_MODE_ERROR", e)
        }
    }

    @ReactMethod
    fun isStreamRecordingSupported(promise: Promise) {
        promise.resolve(StreamRecorder.isSupported())
    }

    @ReactMethod
    fun startStreamRecording(
        streamType: String,
        peerConnectionId: Int,
        videoTrackId: String?,
        includeRemoteAudio: Boolean,
        promise: Promise,
    ) {
        try {
            val pcId = if (peerConnectionId >= 0) peerConnectionId else null
            StreamRecorder.start(
                reactContext.applicationContext,
                streamType,
                pcId,
                videoTrackId,
                StreamRecorder.resolveWebRtcModule(reactContext),
                includeRemoteAudio,
            )
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("RECORD_START", e)
        }
    }

    @ReactMethod
    fun stopStreamRecording(streamType: String, promise: Promise) {
        try {
            val uri = StreamRecorder.stop(reactContext.applicationContext, streamType)
            promise.resolve(uri)
        } catch (e: Exception) {
            promise.reject("RECORD_STOP", e)
        }
    }

    private fun boostMusicVolume(audioManager: AudioManager) {
        // Use STREAM_VOICE_CALL in IN_COMMUNICATION mode for correct routing.
        val stream = AudioManager.STREAM_VOICE_CALL
        val max = audioManager.getStreamMaxVolume(stream)
        if (max > 0) {
            // 60% is loud enough for monitoring without causing physical speaker→mic feedback.
            val target = (max * 0.60f).toInt().coerceAtLeast(1)
            audioManager.setStreamVolume(stream, target, 0)
        }
    }

    private fun requestViewerAudioFocus(audioManager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            val request =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun abandonViewerAudioFocus(audioManager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }
}
