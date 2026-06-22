package com.childmonitor.capture

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Launches [StreamBootstrapActivity] when a typed FGS must be started from background.
 * Call from a worker thread — blocks until bootstrap completes or times out.
 */
object StreamFgsBootstrap {

    private val latch = AtomicReference<CountDownLatch?>(null)

    @Volatile
    private var pendingKind: StreamKind? = null

    @JvmStatic
    fun ensureActive(context: Context, kind: StreamKind, timeoutMs: Long = 8000L): Boolean {
        if (StreamFgsHelper.isActive(kind)) {
            return true
        }
        // Pre-warmed FGS from MonitoringForegroundService avoids the bootstrap activity.
        val directMs = minOf(timeoutMs / 2, 4000L)
        if (StreamFgsPreparer.ensureKindReady(context, kind, directMs)) {
            return true
        }
        val wait = CountDownLatch(1)
        latch.set(wait)
        pendingKind = kind
        try {
            // Use BAL-safe launcher: tries direct → accessibility → fullScreenIntent notification.
            StreamBootstrapActivity.startFgsOnlyWithFallback(context.applicationContext, kind)
            val signaled = wait.await(timeoutMs, TimeUnit.MILLISECONDS)
            return signaled && StreamFgsHelper.isActive(kind)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        } finally {
            pendingKind = null
            latch.compareAndSet(wait, null)
        }
    }

    internal fun signalComplete(@Suppress("UNUSED_PARAMETER") success: Boolean) {
        latch.get()?.countDown()
    }
}
