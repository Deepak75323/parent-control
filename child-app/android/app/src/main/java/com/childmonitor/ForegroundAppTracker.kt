package com.childmonitor

import java.util.concurrent.atomic.AtomicReference

/** Tracks the last foreground package reported by accessibility window events. */
object ForegroundAppTracker {
    private val lastPackage = AtomicReference<String?>(null)

    fun update(packageName: String?) {
        if (!packageName.isNullOrBlank()) {
            lastPackage.set(packageName)
        }
    }

    fun current(): String? = lastPackage.get()
}
