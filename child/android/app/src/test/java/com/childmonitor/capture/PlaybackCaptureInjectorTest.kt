package com.childmonitor.capture

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackCaptureInjectorTest {

    @Before
    fun setUp() {
        PlaybackCaptureInjector.resetForTests()
    }

    @After
    fun tearDown() {
        PlaybackCaptureInjector.resetForTests()
    }

    @Test
    fun inactive_leavesBufferUnchanged() {
        val buf = ByteBuffer.allocate(4)
        buf.put(byteArrayOf(1, 2, 3, 4))
        buf.rewind()
        PlaybackCaptureInjector.fillWebRtcBuffer(1, 48_000, buf)
        assertEquals(1.toByte(), buf.get(0))
    }

    @Test
    fun active_overwritesWithPlaybackPcm() {
        PlaybackCaptureInjector.setActive(true)
        PlaybackCaptureInjector.onPlaybackPcm(byteArrayOf(9, 8, 7, 6), channels = 1, sampleRate = 48_000)
        val buf = ByteBuffer.allocate(4)
        buf.put(byteArrayOf(0, 0, 0, 0))
        buf.rewind()
        PlaybackCaptureInjector.fillWebRtcBuffer(1, 48_000, buf)
        assertTrue(PlaybackCaptureInjector.isActive())
        assertEquals(9.toByte(), buf.get(0))
        assertEquals(8.toByte(), buf.get(1))
    }

    @Test
    fun deactivate_clearsInjection() {
        PlaybackCaptureInjector.setActive(true)
        PlaybackCaptureInjector.onPlaybackPcm(byteArrayOf(1, 2), 1, 48_000)
        PlaybackCaptureInjector.setActive(false)
        assertFalse(PlaybackCaptureInjector.isActive())
    }
}
