package com.childmonitor.capture

import android.content.Context
import android.util.Log
import com.oney.WebRTCModule.WebRTCModuleOptions
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Starts/stops device playback capture and primes the shared WebRTC ADM so mic frames
 * are replaced with system audio PCM ([PlaybackCaptureInjector]).
 */
object DevicePlaybackCaptureSession {

    private const val TAG = "DevicePlaybackSession"

    /**
     * Ensures MediaProjection is warm, microphone FGS is up (API 34+ AudioRecord), starts
     * [DevicePlaybackAudioCapturer], and mutes the mic ADM input so injector PCM is sent.
     */
    fun start(context: Context): Boolean {
        if (!DevicePlaybackAudioCapturer.isSupported()) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=unsupported_api")
            return false
        }
        // Do not launch Cast from Audio — MediaProjection is setup / parent Screen only.
        if (!MediaProjectionConsentHelper.hasConsent(context)) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=no_media_projection_consent")
            return false
        }
        if (!MediaProjectionSessionKeeper.ensureReady(context.applicationContext)) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=projection_session_not_ready")
            return false
        }
        // AudioRecord (playback capture) still needs microphone FGS type on API 34+.
        if (!StreamFgsPreparer.ensureKindReadyForStreamRequest(context, StreamKind.AUDIO)) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=audio_fgs_not_ready")
            return false
        }
        val ok = DevicePlaybackAudioCapturer.startFromActiveProjection()
        if (ok) {
            // Guarantee media apps keep playing — never leave MODE_IN_COMMUNICATION active.
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (am != null && am.mode != android.media.AudioManager.MODE_NORMAL) {
                    am.mode = android.media.AudioManager.MODE_NORMAL
                    Log.i(TAG, "restored AudioManager.MODE_NORMAL for playback capture")
                }
            } catch (e: Exception) {
                Log.w(TAG, "MODE_NORMAL restore failed: ${e.message}")
            }
            setAdmMicMuted(true)
            Log.i(TAG, "PLAYBACK_CAPTURE_SESSION_ACTIVE")
        }
        return ok
    }

    fun stop() {
        DevicePlaybackAudioCapturer.stop()
        setAdmMicMuted(false)
        Log.i(TAG, "PLAYBACK_CAPTURE_SESSION_STOPPED")
    }

    private fun setAdmMicMuted(muted: Boolean) {
        try {
            val adm = WebRTCModuleOptions.getInstance().audioDeviceModule as? JavaAudioDeviceModule
            adm?.setMicrophoneMute(muted)
            Log.i(TAG, "ADM_MIC_MUTE muted=$muted")
        } catch (e: Exception) {
            Log.w(TAG, "ADM_MIC_MUTE_FAILED reason=${e.message}")
        }
    }
}
