package com.parentscaffold.capture

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures app playback audio (WebRTC remote stream) to a raw PCM file when a
 * [MediaProjection] token is available. Used alongside video recording and WAV export.
 *
 * Android requires [AudioPlaybackCaptureConfiguration.Builder](MediaProjection) — there is no
 * public no-arg constructor. Callers must supply an active projection (e.g. parent screen-record
 * consent) or recording proceeds without remote audio.
 */
object StreamPlaybackAudioCapture {
    private const val TAG = "StreamPlaybackAudio"
    const val SAMPLE_RATE = 48000
    const val CHANNELS = 2
    const val MIN_PCM_BYTES = 4096

    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)
    private var pcmFile: File? = null

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @Synchronized
    fun start(context: Context, streamType: String, mediaProjection: MediaProjection? = null) {
        if (!isSupported()) {
            throw IllegalStateException("Playback audio capture requires Android 10 or newer")
        }
        if (running.get()) return
        if (mediaProjection == null) {
            Log.e(TAG, "PLAYBACK_CAPTURE_FAILED reason=no_media_projection — recording without remote audio")
            return
        }

        val channelMask = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, channelMask, encoding).coerceAtLeast(SAMPLE_RATE)

        val captureConfig =
            AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUid(android.os.Process.myUid())
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build()

        val recorder =
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .build()

        val cacheDir = context.cacheDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeType = streamType.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        val pcm =
            File(cacheDir, "pcp_audio_${safeType}_$stamp.pcm").apply {
                parentFile?.mkdirs()
            }

        audioRecord = recorder
        pcmFile = pcm
        running.set(true)
        recorder.startRecording()
        Log.i(TAG, "PLAYBACK_CAPTURE_STARTED streamType=$streamType")

        worker =
            Thread {
                writePcmLoop(recorder, pcm, minBuffer)
            }.also { it.start() }
    }

    private fun writePcmLoop(recorder: AudioRecord, pcm: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        try {
            BufferedOutputStream(FileOutputStream(pcm)).use { out ->
                while (running.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        out.write(buffer, 0, read)
                    } else if (read < 0) {
                        Log.e(TAG, "PLAYBACK_CAPTURE_READ_ERROR code=$read")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PLAYBACK_CAPTURE_LOOP_EXIT reason=${e.message}")
        }
    }

    /** Stops capture. Returns PCM file when enough audio was captured, otherwise null. */
    @Synchronized
    fun stop(): File? {
        if (!running.getAndSet(false)) return null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        worker?.join(2_000)
        worker = null

        val pcm = pcmFile
        pcmFile = null
        if (pcm == null || !pcm.exists() || pcm.length() < MIN_PCM_BYTES) {
            pcm?.delete()
            return null
        }
        return pcm
    }
}
