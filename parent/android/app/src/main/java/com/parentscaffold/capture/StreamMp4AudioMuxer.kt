package com.parentscaffold.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Muxes a video-only MP4 with PCM playback-capture audio into a single AAC/H.264 MP4.
 */
object StreamMp4AudioMuxer {
    private const val AUDIO_BIT_RATE = 128_000
    private const val TIMEOUT_US = 10_000L

    fun mux(videoFile: File, pcmFile: File, outputFile: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return false
        if (!videoFile.exists() || !pcmFile.exists()) return false

        val pcm = pcmFile.readBytes()
        if (pcm.size < StreamPlaybackAudioCapture.MIN_PCM_BYTES) return false

        val encoded = encodePcmToAac(pcm) ?: return false
        val audioFormat = encoded.first
        val audioSamples = encoded.second
        if (audioSamples.isEmpty()) return false

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(videoFile.absolutePath)
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }
            if (videoTrackIndex < 0 || videoFormat == null) return false

            extractor.selectTrack(videoTrackIndex)
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var lastVideoPtsUs = 0L

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                lastVideoPtsUs = bufferInfo.presentationTimeUs
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                extractor.advance()
            }

            val videoDurationUs = (lastVideoPtsUs + 33_333L).coerceAtLeast(1L)
            val pcmDurationUs =
                (pcm.size.toLong() * 1_000_000L) /
                    (StreamPlaybackAudioCapture.SAMPLE_RATE * StreamPlaybackAudioCapture.CHANNELS * 2L)
            val durationUs = minOf(videoDurationUs, pcmDurationUs.coerceAtLeast(1L))

            for (sample in audioSamples) {
                if (sample.info.presentationTimeUs > durationUs) break
                muxer.writeSampleData(muxerAudioTrack, sample.data, sample.info)
            }

            muxer.stop()
            muxer.release()
            return outputFile.exists() && outputFile.length() > 1024
        } catch (_: Exception) {
            outputFile.delete()
            return false
        } finally {
            extractor.release()
        }
    }

    private data class EncodedSample(val data: ByteBuffer, val info: MediaCodec.BufferInfo)

    private fun encodePcmToAac(pcm: ByteArray): Pair<MediaFormat, List<EncodedSample>>? {
        val sampleRate = StreamPlaybackAudioCapture.SAMPLE_RATE
        val channels = StreamPlaybackAudioCapture.CHANNELS
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val samples = mutableListOf<EncodedSample>()
        var outputFormat: MediaFormat? = null
        var inputOffset = 0
        var inputDone = false
        var outputDone = false
        val bufferInfo = MediaCodec.BufferInfo()
        val bytesPerSample = channels * 2

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                        inputBuffer.clear()
                        val chunkSize = minOf(inputBuffer.remaining(), pcm.size - inputOffset)
                        if (chunkSize > 0) {
                            inputBuffer.put(pcm, inputOffset, chunkSize)
                            val ptsUs = (inputOffset.toLong() * 1_000_000L) / (sampleRate * bytesPerSample)
                            codec.queueInputBuffer(inputIndex, 0, chunkSize, ptsUs, 0)
                            inputOffset += chunkSize
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else ->
                        if (outputIndex >= 0) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0) {
                                val out = codec.getOutputBuffer(outputIndex) ?: continue
                                val copy = ByteBuffer.allocate(bufferInfo.size)
                                copy.put(out)
                                copy.flip()
                                val info = MediaCodec.BufferInfo()
                                info.set(
                                    0,
                                    bufferInfo.size,
                                    bufferInfo.presentationTimeUs,
                                    bufferInfo.flags,
                                )
                                samples.add(EncodedSample(copy, info))
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            try {
                codec.release()
            } catch (_: Exception) {
            }
        }

        val muxAudioFormat = outputFormat ?: return null
        if (samples.isEmpty()) return null
        return muxAudioFormat to samples
    }
}
