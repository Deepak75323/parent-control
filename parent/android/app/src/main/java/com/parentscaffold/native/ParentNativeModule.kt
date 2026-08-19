package com.parentscaffold.native

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.UIManagerHelper
import androidx.fragment.app.FragmentActivity
import com.parentscaffold.BuildConfig
import com.parentscaffold.capture.StreamRecorder
import com.parentscaffold.security.ParentSecureAuthStore
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

class ParentNativeModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private data class ViewerTransform(val rotation: Float, val scale: Float)

    private val pendingTransforms = mutableMapOf<Int, ViewerTransform>()
    private val layoutListeners = mutableMapOf<Int, View.OnLayoutChangeListener>()
    private val rotatingSinks = mutableMapOf<Int, RotatingVideoSink>()

    /**
     * SurfaceView.setRotation is ignored on many emulator GPUs. Adding extra rotation
     * to each WebRTC [VideoFrame] tilts the actual pixels (parent-only).
     */
    private class RotatingVideoSink(
        private val delegate: VideoSink,
    ) : VideoSink {
        @Volatile
        var extraRotationDeg: Int = 0

        override fun onFrame(frame: VideoFrame) {
            val extra = extraRotationDeg
            if (extra == 0) {
                delegate.onFrame(frame)
                return
            }
            val rotated =
                VideoFrame(
                    frame.buffer,
                    (frame.rotation + extra) % 360,
                    frame.timestampNs,
                )
            delegate.onFrame(rotated)
        }
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusListener = OnAudioFocusChangeListener { /* listen-only */ }

    override fun getName(): String = "ParentNative"

    override fun getConstants(): MutableMap<String, Any> =
        hashMapOf("mapsAvailable" to BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank())

    /**
     * Viewer-only rotate/zoom. Zoom is JS layout. Rotation is extra VideoFrame rotation
     * (SurfaceView View.rotation does not tilt pixels on emulator / many devices).
     */
    @ReactMethod
    fun applyViewerVideoTransform(reactTag: Int, rotationDeg: Double, scale: Double, promise: Promise) {
        val rot = rotationDeg.toFloat()
        val sc = scale.toFloat().coerceIn(0.25f, 8f)
        pendingTransforms[reactTag] = ViewerTransform(rot, sc)

        UiThreadUtil.runOnUiThread {
            try {
                val uiManager = UIManagerHelper.getUIManagerForReactTag(reactContext, reactTag)
                if (uiManager == null) {
                    promise.reject("NO_UIMANAGER", "UIManager unavailable for reactTag=$reactTag")
                    return@runOnUiThread
                }
                val host = uiManager.resolveView(reactTag)
                if (host == null) {
                    promise.reject("NO_VIEW", "No view for reactTag=$reactTag")
                    return@runOnUiThread
                }
                applyViewerTransform(reactTag, host, rot, sc)
                ensureLayoutListener(reactTag, host)
                if (host.width == 0 || host.height == 0) {
                    host.post { applyViewerTransform(reactTag, host, rot, sc) }
                }
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("VIEWER_TRANSFORM", e)
            }
        }
    }

    private fun ensureLayoutListener(reactTag: Int, host: View) {
        if (layoutListeners.containsKey(reactTag)) {
            return
        }
        val listener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val pending = pendingTransforms[reactTag] ?: return@OnLayoutChangeListener
                applyViewerTransform(reactTag, host, pending.rotation, pending.scale)
            }
        host.addOnLayoutChangeListener(listener)
        layoutListeners[reactTag] = listener
    }

    private fun findWebRTCView(root: View): View? {
        if (root.javaClass.name.endsWith("WebRTCView")) {
            return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findWebRTCView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun applyViewerTransform(reactTag: Int, host: View, rotation: Float, scale: Float) {
        // Clear leftover View transforms from earlier attempts (those skew SurfaceView).
        host.rotation = 0f
        host.scaleX = 1f
        host.scaleY = 1f
        val webrtcView = findWebRTCView(host) ?: host
        webrtcView.rotation = 0f
        webrtcView.scaleX = 1f
        webrtcView.scaleY = 1f
        installFrameRotation(reactTag, webrtcView, Math.round(rotation))
    }

    private fun installFrameRotation(reactTag: Int, webrtcView: View, extraRotation: Int) {
        try {
            val rendererField = webrtcView.javaClass.getDeclaredField("surfaceViewRenderer")
            rendererField.isAccessible = true
            val renderer = rendererField.get(webrtcView) as? VideoSink ?: return

            val trackField = webrtcView.javaClass.getDeclaredField("videoTrack")
            trackField.isAccessible = true
            val track = trackField.get(webrtcView) as? VideoTrack ?: return

            val sink =
                rotatingSinks.getOrPut(reactTag) {
                    RotatingVideoSink(renderer)
                }
            sink.extraRotationDeg = extraRotation
            track.removeSink(renderer)
            track.removeSink(sink)
            track.addSink(sink)
        } catch (e: Exception) {
            Log.w("ParentNative", "installFrameRotation failed", e)
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
