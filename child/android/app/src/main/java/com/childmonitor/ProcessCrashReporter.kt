package com.childmonitor

import android.app.Application
import android.util.Log

/**
 * Records uncaught process crashes so the next heartbeat can flag [PROCESS_CRASH_RECENT] for the
 * parent dashboard and adb logcat triage.
 */
object ProcessCrashReporter {

    private const val TAG = "ChildCrashReporter"

    fun install(application: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "UNCAUGHT_CRASH thread=${thread.name}", error)
            MonitoringPrefs.recordUncaughtCrash(application.applicationContext, error)
            previous?.uncaughtException(thread, error)
        }
    }
}
