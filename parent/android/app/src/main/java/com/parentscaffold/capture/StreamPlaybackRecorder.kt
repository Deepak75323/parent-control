package com.parentscaffold.capture

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures audio played by this app (WebRTC remote stream) via AudioPlaybackCapture (API 29+)
 * and writes a WAV file into the device Downloads folder.
 */
object StreamPlaybackRecorder {
    fun isSupported(): Boolean = StreamPlaybackAudioCapture.isSupported()

    @Synchronized
    fun start(context: Context, streamType: String) {
        StreamPlaybackAudioCapture.start(context, streamType)
    }

    @Synchronized
    fun stop(context: Context, streamType: String): String {
        val pcm =
            StreamPlaybackAudioCapture.stop()
                ?: throw IllegalStateException("Nothing was captured — unmute audio or use the Audio stream")
        return try {
            saveWavToDownloads(context, pcm, streamType)
        } finally {
            pcm.delete()
        }
    }

    private fun saveWavToDownloads(context: Context, pcm: File, streamType: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeType = streamType.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        val displayName = "pcp_${safeType}_$stamp.wav"
        val pcmBytes = pcm.readBytes()
        val wavBytes =
            pcmToWav(
                pcmBytes,
                StreamPlaybackAudioCapture.SAMPLE_RATE,
                StreamPlaybackAudioCapture.CHANNELS,
            )
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ParentalControl")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create download file")
        resolver.openOutputStream(uri)?.use { it.write(wavBytes) }
            ?: throw IllegalStateException("Could not write recording")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    }

    private fun pcmToWav(pcm: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(pcm.size)
        return header.array() + pcm
    }
}
