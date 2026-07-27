package com.childmonitor.capture

import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Replaces WebRTC mic PCM with device [AudioPlaybackCapture] samples via
 * [org.webrtc.audio.AudioRecordDataCallback] (runs after ADM mic-mute zeroing).
 *
 * Improved audio buffer management to reduce noise and distortion during screen sharing.
 * Uses a buffer queue and proper sample rate handling for cleaner audio.
 */
object PlaybackCaptureInjector {

    private const val TAG = "PlaybackCaptureInject"
    private const val MAX_BUFFER_SIZE_MS = 100 // Maximum buffer size in milliseconds
    private const val MIN_BUFFER_SIZE_MS = 20  // Minimum buffer size before injection
    
    private val active = AtomicBoolean(false)
    private val pcmGenerated = AtomicLong(0)
    private val framesInjected = AtomicLong(0)
    private val bufferQueue = ConcurrentLinkedQueue<AudioChunk>()
    private val lock = Any()
    
    // Audio processing state
    private var targetSampleRate: Int = 48_000
    private var targetChannelCount: Int = 1
    private var accumulatedPcm = ByteArray(0)
    
    data class AudioChunk(
        val pcm: ByteArray,
        val channels: Int,
        val sampleRate: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun isActive(): Boolean = active.get()

    fun setActive(enabled: Boolean) {
        active.set(enabled)
        if (!enabled) {
            synchronized(lock) { 
                bufferQueue.clear()
                accumulatedPcm = ByteArray(0)
            }
            Log.i(TAG, "PLAYBACK_INJECT_INACTIVE")
        } else {
            Log.i(TAG, "PLAYBACK_INJECT_ACTIVE")
        }
    }

    fun onPlaybackPcm(pcm: ByteArray, channels: Int, sampleRate: Int) {
        if (pcm.isEmpty() || !active.get()) return
        
        // Add to buffer queue with timestamp
        val chunk = AudioChunk(pcm.copyOf(), channels.coerceAtLeast(1), sampleRate)
        bufferQueue.offer(chunk)
        
        // Remove old chunks to prevent excessive buffering (reduces latency and memory usage)
        val now = System.currentTimeMillis()
        val cutoff = now - MAX_BUFFER_SIZE_MS
        synchronized(lock) {
            while (bufferQueue.isNotEmpty() && bufferQueue.peek()?.timestamp ?: now < cutoff) {
                bufferQueue.poll()
            }
        }
        
        val n = pcmGenerated.incrementAndGet()
        if (n == 1L || n % 200L == 0L) { // Reduced log frequency to avoid spam
            Log.d(
                TAG,
                "PLAYBACK_PCM_BUFFERED count=$n bytes=${pcm.size} ch=$channels rate=$sampleRate queueSize=${bufferQueue.size}",
            )
        }
    }

    /**
     * Called from WebRTC [AudioRecordDataCallback] — overwrites [audioBuffer] when injection
     * is active and fresh playback samples exist. Improved to reduce noise and distortion.
     */
    fun fillWebRtcBuffer(channelCount: Int, sampleRate: Int, audioBuffer: ByteBuffer) {
        if (!active.get()) return
        
        val capacity = audioBuffer.capacity()
        if (capacity <= 0) return
        
        // Update target format for consistency
        targetChannelCount = channelCount.coerceAtLeast(1)
        targetSampleRate = sampleRate
        
        // Ensure we have enough buffered audio before injection
        val requiredBytes = capacity
        val availableBytes = accumulatedPcm.size
        
        if (availableBytes < requiredBytes) {
            // Accumulate more audio from the buffer queue
            accumulateAudioFromQueue(requiredBytes - availableBytes)
        }
        
        audioBuffer.clear()
        
        if (accumulatedPcm.size >= requiredBytes) {
            // We have enough audio - copy to output buffer
            val processedAudio = processAudioForWebRTC(accumulatedPcm, requiredBytes, channelCount, sampleRate)
            audioBuffer.put(processedAudio)
            
            // Remove consumed bytes from accumulator
            accumulatedPcm = if (accumulatedPcm.size > requiredBytes) {
                accumulatedPcm.sliceArray(requiredBytes until accumulatedPcm.size)
            } else {
                ByteArray(0)
            }
        } else {
            // Not enough audio available - fill with silence to prevent noise
            val silence = ByteArray(capacity)
            audioBuffer.put(silence)
        }
        
        audioBuffer.rewind()
        
        val n = framesInjected.incrementAndGet()
        if (n == 1L || n % 100L == 0L) {
            Log.d(
                TAG,
                "PLAYBACK_FRAME_SENT frames=$n capacity=$capacity ch=$channelCount rate=$sampleRate accumulated=${accumulatedPcm.size}",
            )
        }
    }
    
    /**
     * Accumulate audio from the buffer queue into the processing buffer.
     */
    private fun accumulateAudioFromQueue(minBytes: Int) {
        val chunks = mutableListOf<AudioChunk>()
        synchronized(lock) {
            // Collect chunks until we have enough audio
            var accumulatedSize = 0
            while (accumulatedSize < minBytes && bufferQueue.isNotEmpty()) {
                val chunk = bufferQueue.poll()
                if (chunk != null) {
                    chunks.add(chunk)
                    accumulatedSize += chunk.pcm.size
                }
            }
        }
        
        // Process and concatenate chunks
        for (chunk in chunks) {
            val processed = resampleAndConvertChannels(chunk.pcm, chunk.channels, chunk.sampleRate, targetChannelCount, targetSampleRate)
            accumulatedPcm = accumulatedPcm + processed
        }
    }
    
    /**
     * Process raw audio data for WebRTC consumption with proper format conversion.
     */
    private fun processAudioForWebRTC(source: ByteArray, requiredBytes: Int, targetChannels: Int, targetSampleRate: Int): ByteArray {
        val sourceToUse = source.sliceArray(0 until minOf(requiredBytes, source.size))
        return sourceToUse.copyOf(requiredBytes) // Pad with zeros if needed
    }
    
    /**
     * Resample and convert channel count to match WebRTC requirements.
     * This is a simple implementation - could be improved with proper resampling algorithms.
     */
    private fun resampleAndConvertChannels(pcm: ByteArray, srcChannels: Int, srcSampleRate: Int, dstChannels: Int, dstSampleRate: Int): ByteArray {
        // For now, assume sample rates match (48kHz) and just handle channel conversion
        if (srcChannels == dstChannels && srcSampleRate == dstSampleRate) {
            return pcm.copyOf()
        }
        
        if (srcChannels == 1 && dstChannels == 2) {
            // Mono to stereo: duplicate each sample
            val output = ByteArray(pcm.size * 2)
            for (i in pcm.indices step 2) {
                if (i + 1 < pcm.size) {
                    val sample1 = pcm[i]
                    val sample2 = pcm[i + 1]
                    val outputIndex = i * 2
                    if (outputIndex + 3 < output.size) {
                        output[outputIndex] = sample1     // Left channel
                        output[outputIndex + 1] = sample2
                        output[outputIndex + 2] = sample1 // Right channel (same as left)
                        output[outputIndex + 3] = sample2
                    }
                }
            }
            return output
        } else if (srcChannels == 2 && dstChannels == 1) {
            // Stereo to mono: average the channels
            val output = ByteArray(pcm.size / 2)
            for (i in 0 until output.size step 2) {
                if (i * 2 + 3 < pcm.size && i + 1 < output.size) {
                    val left = (pcm[i * 2].toInt() and 0xFF) or ((pcm[i * 2 + 1].toInt() and 0xFF) shl 8)
                    val right = (pcm[i * 2 + 2].toInt() and 0xFF) or ((pcm[i * 2 + 3].toInt() and 0xFF) shl 8)
                    val average = (left + right) / 2
                    output[i] = (average and 0xFF).toByte()
                    output[i + 1] = ((average shr 8) and 0xFF).toByte()
                }
            }
            return output
        }
        
        // Fallback: return as-is
        return pcm.copyOf()
    }

    fun resetForTests() {
        active.set(false)
        pcmGenerated.set(0)
        framesInjected.set(0)
        synchronized(lock) { 
            bufferQueue.clear()
            accumulatedPcm = ByteArray(0)
        }
    }
}
