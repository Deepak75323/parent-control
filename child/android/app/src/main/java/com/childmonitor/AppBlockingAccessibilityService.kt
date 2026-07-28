package com.childmonitor

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.childmonitor.accessibility.AccessibilityTamperGuard
import com.childmonitor.accessibility.BlockedAppCloser
import com.childmonitor.accessibility.DeviceAdminTamperGuard
import com.childmonitor.accessibility.MediaProjectionConsentAutomation
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.debug.PermissionDebugLogger

class AppBlockingAccessibilityService : AccessibilityService() {

    companion object {
        /** While staying in-app, re-check limits often enough to catch mid-session overages. */
        private const val SAME_PACKAGE_RECHECK_MS = 8_000L

        @Volatile
        var instance: AppBlockingAccessibilityService? = null
            private set

        /**
         * After a policy sync (e.g. parent just added a limit), immediately enforce against
         * the current foreground app instead of waiting for the next window event.
         */
        fun enforceCurrentForegroundIfBlocked() {
            val svc = instance ?: return
            svc.mainHandler.post { svc.enforceTrackedForeground() }
        }

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
    /** Last time we re-evaluated screen-time for [lastPackage] while staying in that app. */
    private var lastSamePackageRecheckAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoApproveRetryRunnable: Runnable? = null
    private var autoApproveRetryAttempts = 0
    // P1-5: Track which package the current retry loop is targeting so we can restart
    // the loop when MIUI shows a system popup before the real consent dialog appears.
    private var autoApproveRetryPackage: String? = null

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

        // Auto-approve is always on once Accessibility is connected — no separate "arm"
        // window required. Safety comes from the dialog package/text allowlist in
        // MediaProjectionConsentAutomation, not from timing.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            mainHandler.post { tryAutoApproveMediaProjection(packageName) }
            // PiP / overlay windows may not change "foreground package" — sweep blocked pkgs.
            if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                mainHandler.post { sweepBlockedPipWindows() }
            }
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            mainHandler.post {
                DeviceAdminTamperGuard.tryBlockDeactivate(this, packageName)
                DeviceAdminTamperGuard.relockIfLeftSettings(this, packageName)
                AccessibilityTamperGuard.tryBlockDisable(this, packageName, eventType)
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

    private fun tryAutoApproveMediaProjection(packageName: String?) {
        PermissionDebugLogger.enter(
            "MediaProjectionConsentAutomation.tryAutoApprove",
            "pkg=$packageName",
        )
        val clicked = MediaProjectionConsentAutomation.tryAutoApprove(this, packageName)
        PermissionDebugLogger.exit(
            "MediaProjectionConsentAutomation.tryAutoApprove",
            "clicked=$clicked",
        )
        if (clicked) {
            cancelAutoApproveRetries()
            return
        }
        scheduleAutoApproveRetries(packageName)
    }

    /**
     * Dialog nodes may appear after WINDOW_STATE_CHANGED — retry for ~30 s.
     *
     * P1-5 (MIUI system-popup interruption): On Xiaomi MIUI/HyperOS the OS occasionally
     * shows an OEM popup (battery warning, RAM cleaner) *before* the MediaProjection
     * consent dialog. The original guard `if (autoApproveRetryRunnable != null) return`
     * caused the retry loop to stay bound to the OEM popup's package, so when the real
     * consent dialog appeared it was silently ignored. Fix: when a new window from a
     * known consent-dialog package arrives while we are still retrying for a *different*
     * package, cancel the stale loop and restart immediately for the consent package.
     */
    private fun scheduleAutoApproveRetries(packageName: String?) {
        if (autoApproveRetryRunnable != null) {
            // Allow restart only when the incoming package is a known consent host and
            // differs from what the current loop is targeting — covers the MIUI race.
            val newPackageIsConsent = MediaProjectionConsentAutomation.isKnownConsentPackage(packageName)
            val packageChanged = autoApproveRetryPackage != packageName
            if (newPackageIsConsent && packageChanged) {
                android.util.Log.i(
                    "AppBlockingA11y",
                    "scheduleAutoApproveRetries — consent package changed " +
                        "from=${autoApproveRetryPackage} to=$packageName; restarting retry loop",
                )
                cancelAutoApproveRetries()
                // Fall through to start a fresh loop below.
            } else {
                return
            }
        }
        autoApproveRetryPackage = packageName
        autoApproveRetryAttempts = 0
        val runnable = object : Runnable {
            override fun run() {
                // Cancel retries only when a live MediaProjection exists. Stale in-memory
                // Intent with a dead binder must keep retrying so Cast auto-approve can fire.
                if (com.childmonitor.capture.ScreenCaptureController.hasLiveProjection()) {
                    android.util.Log.d(
                        "AppBlockingA11y",
                        "scheduleAutoApproveRetries: live projection ready — cancelling retries",
                    )
                    cancelAutoApproveRetries()
                    return
                }
                val clicked = MediaProjectionConsentAutomation.tryAutoApprove(
                    this@AppBlockingAccessibilityService,
                    packageName,
                )
                if (clicked) {
                    cancelAutoApproveRetries()
                    return
                }
                autoApproveRetryAttempts++
                if (autoApproveRetryAttempts < 75) {
                    mainHandler.postDelayed(this, 400L)
                } else {
                    cancelAutoApproveRetries()
                }
            }
        }
        autoApproveRetryRunnable = runnable
        mainHandler.postDelayed(runnable, 200L)
    }

    private fun cancelAutoApproveRetries() {
        autoApproveRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        autoApproveRetryRunnable = null
        autoApproveRetryAttempts = 0
        autoApproveRetryPackage = null
    }

    private fun handlePolicyEvent(event: AccessibilityEvent) {
        PermissionDebugLogger.enter(
            "AppBlockingAccessibilityService.handlePolicyEvent",
            "type=${event.eventType} pkg=${event.packageName}",
        )
        // WINDOW_STATE_CHANGED is preferred; on MIUI, app switches sometimes only emit
        // WINDOW_CONTENT_CHANGED. Accept that for package-change / screen-time rechecks.
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
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
        val nowMs = System.currentTimeMillis()
        if (pkg == lastPackage) {
            // Stay-in-app: still re-check screen-time limits periodically so a 15-min YouTube
            // limit fires while the child keeps watching (not only on next app switch).
            if (nowMs - lastSamePackageRecheckAt < SAME_PACKAGE_RECHECK_MS) {
                PermissionDebugLogger.exit(
                    "AppBlockingAccessibilityService.handlePolicyEvent",
                    "duplicate pkg (recheck deferred)",
                )
                return
            }
            lastSamePackageRecheckAt = nowMs
        } else {
            lastPackage = pkg
            lastSamePackageRecheckAt = nowMs
            ForegroundAppTracker.update(pkg)
        }

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
            PermissionDebugLogger.event("AppBlockingAccessibilityService", "enforceBlock pkg=$pkg")
            BlockedAppCloser.enforce(this, pkg, reason)
        }
        PermissionDebugLogger.exit("AppBlockingAccessibilityService.handlePolicyEvent", "blocked pkg=$pkg")
    }

    private fun enforceTrackedForeground() {
        val pkg = ForegroundAppTracker.current() ?: lastPackage ?: return
        if (pkg == applicationContext.packageName) return
        PolicyEnforcementEngine.reload(applicationContext)
        if (!PolicyEnforcementEngine.isPackageBlocked(applicationContext, pkg)) return
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < 1500) return
        lastBlockAt = now
        val reason = if (PolicyEnforcementEngine.isScreenTimeOnlyBlock(applicationContext, pkg)) {
            "time_limit"
        } else {
            "blocked"
        }
        PermissionDebugLogger.event(
            "AppBlockingAccessibilityService",
            "enforceCurrentForeground pkg=$pkg reason=$reason",
        )
        PolicyEnforcementEngine.reportViolation(
            applicationContext,
            "APP_BLOCKED_ATTEMPT",
            pkg,
            null,
        )
        BlockedAppCloser.enforce(this, pkg, reason)
    }

    /**
     * When YouTube enters PiP, the "foreground" package becomes the launcher — still close
     * any on-screen windows belonging to a blocked package.
     */
    private fun sweepBlockedPipWindows() {
        val windows = try {
            windows
        } catch (_: Exception) {
            return
        } ?: return
        val seen = HashSet<String>()
        for (window in windows) {
            val root = window.root ?: continue
            try {
                val pkg = root.packageName?.toString() ?: continue
                if (!seen.add(pkg)) continue
                if (pkg == applicationContext.packageName) continue
                if (!PolicyEnforcementEngine.isPackageBlocked(applicationContext, pkg)) continue
                val now = System.currentTimeMillis()
                if (now - lastBlockAt < 1500) continue
                lastBlockAt = now
                val reason =
                    if (PolicyEnforcementEngine.isScreenTimeOnlyBlock(applicationContext, pkg)) {
                        "time_limit"
                    } else {
                        "blocked"
                    }
                PermissionDebugLogger.event(
                    "AppBlockingAccessibilityService",
                    "sweepBlockedPip pkg=$pkg",
                )
                BlockedAppCloser.enforce(this, pkg, reason)
            } finally {
                root.recycle()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        cancelAutoApproveRetries()
        AccessibilityConnectionNotifier.onServiceDisconnected(applicationContext)
        instance = null
        eventExecutor.shutdownNow()
        super.onDestroy()
    }
}
