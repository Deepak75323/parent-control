package com.childmonitor

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.childmonitor.accessibility.MediaProjectionConsentAutomation
import com.childmonitor.debug.PermissionDebugLogger

class AppBlockingAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AppBlockingAccessibilityService? = null
            private set

        /**
         * Attempt to start an activity from the accessibility service context.
         *
         * On MIUI/HyperOS, a running AccessibilityService context is not subject to
         * the same Background Activity Launch (BAL) restrictions as a plain service
         * context — the system grants it an implicit "foreground" token.  This is
         * layer-2 of our BAL fallback chain (notification fullScreenIntent is layer-1).
         *
         * Returns true if the service was alive and startActivity() did not throw.
         * Note: MIUI may still block on some ROM versions; callers should treat `true`
         * as "attempted" rather than "succeeded".
         */
        fun tryStartActivity(intent: android.content.Intent): Boolean {
            val svc = instance ?: return false
            return try {
                svc.startActivity(intent)
                true
            } catch (e: Exception) {
                android.util.Log.w("AppBlockingA11y", "tryStartActivity failed: ${e.message}")
                false
            }
        }
    }

    private var lastPackage: String? = null
    private var lastBlockAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * A2 fix: replace the unbounded single-thread executor with a bounded one.
     *
     * The previous Executors.newSingleThreadExecutor() used a LinkedBlockingQueue
     * with Integer.MAX_VALUE capacity.  On a gaming device generating 50+ accessibility
     * events per second, with each event taking 10–50 ms to process (policy reload from
     * SharedPreferences + package name lookup), the queue grew unboundedly and eventually
     * caused an OutOfMemoryError, killing all monitoring.
     *
     * Fix: bounded ArrayBlockingQueue(64) with DiscardOldestPolicy.  Dropping the oldest
     * event is safe for app-blocking — we only care about the *current* foreground app,
     * so a stale queued event for a previous screen is discarded rather than processed
     * out of order. The policy reload cache (below) further reduces processing time.
     */
    private val eventExecutor = java.util.concurrent.ThreadPoolExecutor(
        1, 1,
        0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue(64),
        java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AccessibilityConnectionNotifier.onServiceConnected(applicationContext)
        Thread {
            com.childmonitor.sync.CommandRecoveryManager
                .onAccessibilityConnected(applicationContext)
        }.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val snapshot = AccessibilityEvent.obtain(event)
        val packageName = snapshot.packageName?.toString()
        val eventType = snapshot.eventType

        if (MonitoringPrefs.isScreenCaptureAutoApproveArmed(this) &&
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            mainHandler.post {
                PermissionDebugLogger.enter(
                    "MediaProjectionConsentAutomation.tryAutoApprove",
                    "pkg=$packageName",
                )
                val clicked = MediaProjectionConsentAutomation.tryAutoApprove(this, packageName)
                PermissionDebugLogger.exit(
                    "MediaProjectionConsentAutomation.tryAutoApprove",
                    "clicked=$clicked",
                )
            }
        }

        eventExecutor.execute {
            try {
                handlePolicyEvent(snapshot)
            } finally {
                snapshot.recycle()
            }
        }
    }

    private fun handlePolicyEvent(event: AccessibilityEvent) {
        PermissionDebugLogger.enter(
            "AppBlockingAccessibilityService.handlePolicyEvent",
            "type=${event.eventType} pkg=${event.packageName}",
        )
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            PermissionDebugLogger.exit("AppBlockingAccessibilityService.handlePolicyEvent", "ignored type")
            return
        }
        val pkg = event.packageName?.toString() ?: run {
            PermissionDebugLogger.exit("AppBlockingAccessibilityService.handlePolicyEvent", "no pkg")
            return
        }
        if (pkg == applicationContext.packageName) {
            PermissionDebugLogger.exit("AppBlockingAccessibilityService.handlePolicyEvent", "self pkg")
            return
        }
        if (pkg == lastPackage) {
            PermissionDebugLogger.exit("AppBlockingAccessibilityService.handlePolicyEvent", "duplicate pkg")
            return
        }
        lastPackage = pkg
        ForegroundAppTracker.update(pkg)

        PolicyEnforcementEngine.reload(applicationContext)

        if (!PolicyEnforcementEngine.isPackageBlocked(applicationContext, pkg)) {
            PermissionDebugLogger.exit("AppBlockingAccessibilityService.handlePolicyEvent", "not blocked")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastBlockAt < 1500) {
            PermissionDebugLogger.exit("AppBlockingAccessibilityService.handlePolicyEvent", "block debounced")
            return
        }
        lastBlockAt = now

        PolicyEnforcementEngine.reportViolation(
            applicationContext,
            "APP_BLOCKED_ATTEMPT",
            pkg,
            null,
        )
        val reason = if (PolicyEnforcementEngine.isScreenTimeOnlyBlock(applicationContext, pkg)) {
            "time_limit"
        } else {
            "blocked"
        }
        mainHandler.post {
            PermissionDebugLogger.event("AppBlockingAccessibilityService", "showBlockScreen pkg=$pkg")
            PolicyEnforcementEngine.showBlockScreen(applicationContext, pkg, reason)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        PermissionDebugLogger.exit("AppBlockingAccessibilityService.handlePolicyEvent", "blocked pkg=$pkg")
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        AccessibilityConnectionNotifier.onServiceDisconnected(applicationContext)
        instance = null
        eventExecutor.shutdownNow()
        super.onDestroy()
    }
}
