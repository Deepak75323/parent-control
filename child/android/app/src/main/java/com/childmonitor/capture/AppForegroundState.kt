package com.childmonitor.capture

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks whether any of our activities is visible — required for Android 14+
 * while-in-use camera/microphone foreground-service promotion.
 */
object AppForegroundState {

    private val visibleActivities = AtomicInteger(0)

    @JvmStatic
    fun onActivityVisible() {
        visibleActivities.incrementAndGet()
    }

    @JvmStatic
    fun onActivityHidden() {
        visibleActivities.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    @JvmStatic
    fun hasVisibleActivity(): Boolean = visibleActivities.get() > 0

    /**
     * True when the process is eligible to promote a camera/mic typed FGS.
     * Background / cached processes must bootstrap via [StreamBootstrapActivity] first.
     */
    @JvmStatic
    fun canPromoteWhileInUseFgs(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        if (hasVisibleActivity()) {
            return true
        }
        return try {
            val am = context.getSystemService(ActivityManager::class.java) ?: return false
            val procs = am.runningAppProcesses ?: return false
            val me = procs.firstOrNull { it.pid == Process.myPid() } ?: return false
            me.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } catch (_: Exception) {
            false
        }
    }
}
