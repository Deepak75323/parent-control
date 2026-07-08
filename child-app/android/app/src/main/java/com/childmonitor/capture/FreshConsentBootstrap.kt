package com.childmonitor.capture

import android.content.Intent
import java.util.concurrent.atomic.AtomicReference

/** One-shot fresh MediaProjection consent passed from [MediaProjectionConsentActivity]. */
object FreshConsentBootstrap {

    data class Payload(val resultCode: Int, val data: Intent)

    private val pending = AtomicReference<Payload?>(null)

    @JvmStatic
    fun set(resultCode: Int, data: Intent) {
        pending.set(Payload(resultCode, data))
    }

    @JvmStatic
    fun take(): Payload? = pending.getAndSet(null)

    @JvmStatic
    fun clear() {
        pending.set(null)
    }
}
