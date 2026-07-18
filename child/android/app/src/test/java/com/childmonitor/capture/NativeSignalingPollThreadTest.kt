package com.childmonitor.capture

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Regression: NativeStreamPublisher used to poll signaling on the main looper.
 * That throws NetworkOnMainThreadException and prevents the parent answer from
 * being applied (parent stuck on "Waiting for screen…"), especially after RN
 * is paused when the child Activity is removed from recents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeSignalingPollThreadTest {

    @Test
    fun signalingPollHandlerUsesBackgroundLooper() {
        val thread = HandlerThread("NativeSignalingPoll").also { it.start() }
        try {
            val handler = Handler(thread.looper)
            assertNotEquals(Looper.getMainLooper(), handler.looper)

            val ranOffMain = AtomicBoolean(false)
            val done = CountDownLatch(1)
            handler.post {
                ranOffMain.set(Looper.myLooper() != Looper.getMainLooper())
                done.countDown()
            }
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertTrue(ranOffMain.get())
        } finally {
            thread.quitSafely()
        }
    }
}
