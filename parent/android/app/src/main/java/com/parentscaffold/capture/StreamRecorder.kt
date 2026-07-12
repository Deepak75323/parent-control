package com.parentscaffold.capture

import android.content.Context
import com.facebook.react.bridge.ReactApplicationContext
import com.oney.WebRTCModule.WebRTCModule
import java.util.Locale

/**
 * Routes stream recording to audio (WAV) or video (MP4) capture depending on stream type.
 */
object StreamRecorder {
    fun isSupported(): Boolean = StreamPlaybackRecorder.isSupported()

    @Synchronized
    fun start(
        context: Context,
        streamType: String,
        peerConnectionId: Int?,
        videoTrackId: String?,
        webrtcModule: WebRTCModule?,
        includeRemoteAudio: Boolean,
    ) {
        when (streamType.uppercase(Locale.US)) {
            "AUDIO" -> StreamPlaybackRecorder.start(context, streamType)
            "SCREEN", "CAMERA" -> {
                val pcId = peerConnectionId
                val trackId = videoTrackId
                if (pcId == null || trackId.isNullOrBlank()) {
                    throw IllegalStateException("Video track info is required for $streamType recording")
                }
                val module =
                    webrtcModule
                        ?: throw IllegalStateException("WebRTC is not available")
                StreamVideoRecorder.start(context, streamType, pcId, trackId, module, includeRemoteAudio)
            }
            else -> throw IllegalArgumentException("Unsupported stream type: $streamType")
        }
    }

    @Synchronized
    fun stop(context: Context, streamType: String): String {
        return when (streamType.uppercase(Locale.US)) {
            "AUDIO" -> StreamPlaybackRecorder.stop(context, streamType)
            "SCREEN", "CAMERA" -> StreamVideoRecorder.stop(context, streamType)
            else -> throw IllegalArgumentException("Unsupported stream type: $streamType")
        }
    }

    fun resolveWebRtcModule(reactContext: ReactApplicationContext): WebRTCModule? =
        reactContext.getNativeModule(WebRTCModule::class.java)
}
