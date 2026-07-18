package com.childmonitor.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.childmonitor.platform.Android16FeatureFlags
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaProjectionCaptureGateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaProjectionCaptureGate.forceRelease("test-setup")
        Android16FeatureFlags.setFlag(context, Android16FeatureFlags.KEY_STRICT_PROJECTION_GATE, true)
    }

    @After
    fun tearDown() {
        MediaProjectionCaptureGate.forceRelease("test-teardown")
    }

    @Test
    fun tryAcquire_blocksSecondOwner() {
        assertTrue(MediaProjectionCaptureGate.tryAcquire("a", context))
        assertFalse(MediaProjectionCaptureGate.tryAcquire("b", context))
        MediaProjectionCaptureGate.release("a")
        assertTrue(MediaProjectionCaptureGate.tryAcquire("b", context))
    }

    @Test
    fun tryAcquire_sameOwner_isIdempotent() {
        assertTrue(MediaProjectionCaptureGate.tryAcquire("a", context))
        assertTrue(MediaProjectionCaptureGate.tryAcquire("a", context))
        assertTrue(MediaProjectionCaptureGate.isHeld())
    }

    @Test
    fun forceAcquire_stealsFromOtherOwner() {
        assertTrue(MediaProjectionCaptureGate.tryAcquire("old", context))
        assertTrue(MediaProjectionCaptureGate.forceAcquire("new", "stale_test"))
        assertTrue(MediaProjectionCaptureGate.isHeld())
        assertTrue(MediaProjectionCaptureGate.activeOwner() == "new")
        MediaProjectionCaptureGate.release("new")
        assertFalse(MediaProjectionCaptureGate.isHeld())
    }

    @Test
    fun forceRelease_clearsAnyOwner() {
        assertTrue(MediaProjectionCaptureGate.tryAcquire("stuck", context))
        MediaProjectionCaptureGate.forceRelease("teardown")
        assertFalse(MediaProjectionCaptureGate.isHeld())
        assertTrue(MediaProjectionCaptureGate.tryAcquire("fresh", context))
    }

    @Test
    fun stableGateOwner_constant() {
        assertTrue(ReuseProjectionCapturer.GATE_OWNER == "ReuseProjectionCapturer")
    }
}
