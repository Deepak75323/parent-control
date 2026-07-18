package com.childmonitor.capture

import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Replaces WebRTC mic PCM with device [AudioPlaybackCapture] samples via
 * [org.webrtc.audio.AudioRecordDataCallback] (runs after ADM mic-mute zeroing).
 *
 * Thread-safe ring of the latest capture buffer so 10 ms WebRTC frames can pull
 * mono/stereo PCM without blocking the capture thread.
 */
object PlaybackCaptureInjector {

    private const val TAG = "PlaybackCaptureInject"
    private val active = AtomicBoolean(false)
    private val pcmGenerated = AtomicLong(0)
    private val framesInjected = AtomicLong(0)
    private val lock = Any()
    private var latest: ByteArray = ByteArray(0)
    private var latestChannels: Int = 1
    private var latestSampleRate: Int = 48_000

    fun isActive(): Boolean = active.get()

    fun setActive(enabled: Boolean) {
        active.set(enabled)
        if (!enabled) {
            synchronized(lock) { latest = ByteArray(0) }
            Log.i(TAG, "PLAYBACK_INJECT_INACTIVE")
        } else {
            Log.i(TAG, "PLAYBACK_INJECT_ACTIVE")
        }
    }

    fun onPlaybackPcm(pcm: ByteArray, channels: Int, sampleRate: Int) {
        if (pcm.isEmpty()) return
        synchronized(lock) {
            latest = pcm.copyOf()
            latestChannels = channels.coerceAtLeast(1)
            latestSampleRate = sampleRate
        }
        val n = pcmGenerated.incrementAndGet()
        if (n == 1L || n % 100L == 0L) {
            Log.i(
                TAG,
                "PLAYBACK_PCM_GENERATED count=$n bytes=${pcm.size} ch=$channels rate=$sampleRate",
            )
        }
    }

    /**
     * Called from WebRTC [AudioRecordDataCallback] — overwrites [audioBuffer] when injection
     * is active and fresh playback samples exist.
     */
    fun fillWebRtcBuffer(channelCount: Int, sampleRate: Int, audioBuffer: ByteBuffer) {
        if (!active.get()) return
        val capacity = audioBuffer.capacity()
        if (capacity <= 0) return
        val src: ByteArray
        val srcChannels: Int
        synchronized(lock) {
            if (latest.isEmpty()) return
            src = latest
            srcChannels = latestChannels
        }
        audioBuffer.clear()
        val out = ByteArray(capacity)
        val framesOut = capacity / 2 / channelCount.coerceAtLeast(1)
        val framesSrc = src.size / 2 / srcChannels.coerceAtLeast(1)
        var si = 0
        for (f in 0 until framesOut) {
            val srcFrame = if (framesSrc > 0) (f % framesSrc) else 0
            val srcBase = srcFrame * srcChannels * 2
            for (c in 0 until channelCount) {
                val srcCh = (c % srcChannels).coerceAtLeast(0)
                val srcIdx = srcBase + srcCh * 2
                val dest = (f * channelCount + c) * 2
                if (srcIdx + 1 < src.size && dest + 1 < out.size) {
                    out[dest] = src[srcIdx]
                    out[dest + 1] = src[srcIdx + 1]
                }
            }
            si = srcFrame
        }
        audioBuffer.put(out)
        audioBuffer.rewind()
        val n = framesInjected.incrementAndGet()
        if (n == 1L) {
            Log.i(
                TAG,
                "PLAYBACK_FRAME_SENT firstFrame capacity=$capacity ch=$channelCount rate=$sampleRate srcFrames=$si",
            )
        }
    }

    fun resetForTests() {
        active.set(false)
        pcmGenerated.set(0)
        framesInjected.set(0)
        synchronized(lock) { latest = ByteArray(0) }
    }
}
