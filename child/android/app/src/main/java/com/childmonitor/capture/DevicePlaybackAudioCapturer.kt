package com.childmonitor.capture

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures mixed device playback (YouTube, Spotify, games, etc.) via
 * [AudioPlaybackCaptureConfiguration] bound to an active [MediaProjection].
 *
 * Requires Android 10+ (API 29), RECORD_AUDIO, and a live MediaProjection token.
 * Feeds PCM into [PlaybackCaptureInjector] for WebRTC injection.
 */
object DevicePlaybackAudioCapturer {

    private const val TAG = "DevicePlaybackAudio"
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 1

    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun isRunning(): Boolean = running.get()

    @Synchronized
    fun start(projection: MediaProjection): Boolean {
        if (!isSupported()) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=api_lt_29")
            return false
        }
        if (running.get()) {
            Log.i(TAG, "PLAYBACK_CAPTURE_ALREADY_RUNNING")
            return true
        }
        return try {
            val channelMask = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelMask, encoding)
                .coerceAtLeast(SAMPLE_RATE)

            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val recorder = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .build()

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=audio_record_not_initialized")
                return false
            }

            audioRecord = recorder
            running.set(true)
            PlaybackCaptureInjector.setActive(true)
            recorder.startRecording()
            Log.i(TAG, "PLAYBACK_CAPTURE_STARTED sampleRate=$SAMPLE_RATE channels=$CHANNELS")

            worker = Thread({
                val buf = ByteArray(minBuffer)
                while (running.get()) {
                    val read = try {
                        recorder.read(buf, 0, buf.size)
                    } catch (e: Exception) {
                        Log.e(TAG, "PLAYBACK_CAPTURE_READ_FAILED reason=${e.message}")
                        break
                    }
                    if (read > 0) {
                        PlaybackCaptureInjector.onPlaybackPcm(
                            buf.copyOf(read),
                            CHANNELS,
                            SAMPLE_RATE,
                        )
                    } else if (read < 0) {
                        Log.e(TAG, "PLAYBACK_CAPTURE_READ_ERROR code=$read")
                        break
                    }
                }
            }, "device-playback-capture").also { it.start() }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=security ${e.message}", e)
            stop()
            false
        } catch (e: Exception) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=${e.message}", e)
            stop()
            false
        }
    }

    /**
     * Starts capture from [ScreenCaptureController]'s live projection when available.
     */
    fun startFromActiveProjection(): Boolean {
        val projection = ScreenCaptureController.activeProjection()
        if (projection == null) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=no_active_projection")
            return false
        }
        return start(projection)
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false) && audioRecord == null) {
            PlaybackCaptureInjector.setActive(false)
            return
        }
        Log.i(TAG, "PLAYBACK_CAPTURE_STOPPING")
        PlaybackCaptureInjector.setActive(false)
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        try {
            worker?.join(2_000)
        } catch (_: Exception) {
        }
        worker = null
        Log.i(TAG, "PLAYBACK_CAPTURE_STOPPED")
    }
}
