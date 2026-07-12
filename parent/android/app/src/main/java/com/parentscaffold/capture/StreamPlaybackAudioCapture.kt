package com.parentscaffold.capture

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures app playback audio (WebRTC remote stream) to a raw PCM file.
 * Used alongside video recording and for standalone WAV export.
 */
object StreamPlaybackAudioCapture {
    const val SAMPLE_RATE = 48000
    const val CHANNELS = 2
    const val MIN_PCM_BYTES = 4096

    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)
    private var pcmFile: File? = null

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @Synchronized
    fun start(context: Context, streamType: String) {
        if (!isSupported()) {
            throw IllegalStateException("Playback audio capture requires Android 10 or newer")
        }
        if (running.get()) return

        val channelMask = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, channelMask, encoding).coerceAtLeast(SAMPLE_RATE)

        val captureConfig = createPlaybackCaptureConfig()

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

        worker =
            Thread {
                writePcmLoop(recorder, pcm, minBuffer)
            }.also { it.start() }
    }

    private fun createPlaybackCaptureConfig(): AudioPlaybackCaptureConfiguration {
        val builder =
            AudioPlaybackCaptureConfiguration.Builder::class.java
                .getDeclaredConstructor()
                .newInstance() as AudioPlaybackCaptureConfiguration.Builder
        return builder
            .addMatchingUid(android.os.Process.myUid())
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .build()
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
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Worker exits when recording stops.
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
