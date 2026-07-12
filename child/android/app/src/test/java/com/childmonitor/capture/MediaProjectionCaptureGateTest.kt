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
        MediaProjectionCaptureGate.release("a")
        MediaProjectionCaptureGate.release("b")
        Android16FeatureFlags.setFlag(context, Android16FeatureFlags.KEY_STRICT_PROJECTION_GATE, true)
    }

    @After
    fun tearDown() {
        MediaProjectionCaptureGate.release("a")
        MediaProjectionCaptureGate.release("b")
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
}
