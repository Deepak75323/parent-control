package com.childmonitor.capture

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/** Minimal lifecycle for CameraX bindToLifecycle while capture runs outside an Activity. */
internal class CapturerLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private val mainHandler = Handler(Looper.getMainLooper())
    override val lifecycle: Lifecycle = registry

    init {
        setState(Lifecycle.State.CREATED)
    }

    fun markStarted() {
        setState(Lifecycle.State.STARTED)
    }

    fun markStopped() {
        setState(Lifecycle.State.CREATED)
    }

    fun markDestroyed() {
        setState(Lifecycle.State.DESTROYED)
    }

    private fun setState(state: Lifecycle.State) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            registry.currentState = state
        } else {
            mainHandler.post { registry.currentState = state }
        }
    }
}
