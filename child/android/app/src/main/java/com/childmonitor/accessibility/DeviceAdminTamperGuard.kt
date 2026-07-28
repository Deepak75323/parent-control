package com.childmonitor.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.childmonitor.R
import com.childmonitor.tamper.TamperBlockPolicy
import com.childmonitor.tamper.TamperGateController

/**
 * Blocks device-admin deactivation in system Settings until parent PIN or verification code
 * is accepted on [com.childmonitor.tamper.TamperGateActivity].
 *
 * Important: [android.app.admin.DeviceAdminReceiver.onDisableRequested] only shows a warning.
 * If the user taps OK on that system dialog, Android deactivates admin. While tamper is locked
 * we must Cancel that dialog via Accessibility — and must not throttle away those Cancel
 * attempts after we already bounced the user from the deactivate page.
 */
object DeviceAdminTamperGuard {

    private const val TAG = "DeviceAdminTamperGuard"
    private const val PAGE_BLOCK_COOLDOWN_MS = 1200L
    private const val PAGE_SCAN_MIN_MS = 300L
    private const val DIALOG_SWEEP_INTERVAL_MS = 200L
    private const val DIALOG_SWEEP_COUNT = 12 // ~2.4s of retries after a dialog sighting

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPageBlockAt = 0L
    private var lastPageScanAt = 0L
    private var dialogSweepRemaining = 0
    /** True after Settings was seen during an active parent unlock window. */
    @Volatile
    private var sawSettingsWhileUnlocked = false

    /**
     * If parent unlocked to open Settings but left without turning protection off,
     * clear the unlock so the next visit shows the tamper password screen again.
     */
    fun relockIfLeftSettings(service: AccessibilityService, packageName: String?) {
        val unlocked = com.childmonitor.MonitoringPrefs.isTamperUnlocked(service)
        if (!unlocked) {
            sawSettingsWhileUnlocked = false
            return
        }
        val adminActive = com.childmonitor.TamperProtectionManager.isDeviceAdminActive(service)
        val a11yTamper =
            com.childmonitor.MonitoringPrefs.isAccessibilityTamperProtectionEnabled(service) &&
                com.childmonitor.AccessibilityHealthChecker.isAccessibilityEnabled(service)
        val protectionStillActive = adminActive || a11yTamper
        if (!protectionStillActive) {
            sawSettingsWhileUnlocked = false
            return
        }
        val pkg = packageName ?: return
        val isSettings = TamperSettingsHeuristics.isSettingsPackage(pkg)
        val isOurApp = pkg == service.packageName
        if (isSettings) {
            sawSettingsWhileUnlocked = true
            return
        }
        if (TamperBlockPolicy.shouldClearUnlockAfterLeavingSettings(
                tamperUnlocked = true,
                protectionStillActive = true,
                sawSettingsWhileUnlocked = sawSettingsWhileUnlocked,
                packageIsSettings = false,
                packageIsOurApp = isOurApp,
            )
        ) {
            Log.i(TAG, "Left Settings with protection still on — clearing tamper unlock")
            com.childmonitor.MonitoringPrefs.clearTamperUnlock(service)
            sawSettingsWhileUnlocked = false
        }
    }

    fun tryBlockDeactivate(service: AccessibilityService, packageName: String?): Boolean {
        if (!TamperGateController.shouldBlockSettingsInteraction(service)) return false

        // Dialog Cancel is never throttled — OK on this dialog permanently turns admin off.
        if (tryCancelSystemDisableDialog(service)) {
            startDialogSweep(service)
            return true
        }

        val now = System.currentTimeMillis()
        if (now - lastPageBlockAt < PAGE_BLOCK_COOLDOWN_MS) return false
        if (now - lastPageScanAt < PAGE_SCAN_MIN_MS) return false
        lastPageScanAt = now

        if (!TamperSettingsHeuristics.isSettingsPackage(packageName)) return false

        val root = service.rootInActiveWindow ?: return false
        return try {
            if (!treeLooksLikeOurDeviceAdminDeactivate(service, root)) return false
            lastPageBlockAt = now
            Log.i(TAG, "Blocking device admin deactivate page pkg=$packageName")
            TamperGateController.onDisableRequested(service.applicationContext)
            leaveDangerousSettings(service)
            startDialogSweep(service)
            true
        } finally {
            root.recycle()
        }
    }

    /**
     * Keep cancelling the system warning dialog for a short window — MIUI often delivers
     * the dialog after the deactivate-page event, and the first Cancel click can miss.
     */
    private fun startDialogSweep(service: AccessibilityService) {
        dialogSweepRemaining = DIALOG_SWEEP_COUNT
        mainHandler.removeCallbacksAndMessages(service)
        scheduleNextSweep(service)
    }

    private fun scheduleNextSweep(service: AccessibilityService) {
        if (dialogSweepRemaining <= 0) return
        if (!TamperGateController.shouldBlockSettingsInteraction(service)) {
            dialogSweepRemaining = 0
            return
        }
        mainHandler.postDelayed({
            dialogSweepRemaining--
            if (tryCancelSystemDisableDialog(service) || dialogSweepRemaining > 0) {
                scheduleNextSweep(service)
            }
        }, service, DIALOG_SWEEP_INTERVAL_MS)
    }

    private fun tryCancelSystemDisableDialog(service: AccessibilityService): Boolean {
        val warning = service.getString(R.string.tamper_disable_warning).trim().lowercase()
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            val windows = try {
                service.windows
            } catch (_: Exception) {
                null
            }
            if (windows != null) {
                for (window in windows) {
                    window.root?.let { roots.add(it) }
                }
            }
            if (roots.isEmpty()) {
                service.rootInActiveWindow?.let { roots.add(it) }
            }
            for (root in roots) {
                val text = collectText(root)
                if (!TamperSettingsHeuristics.looksLikeAdminDisableWarningDialog(text, warning)) {
                    continue
                }
                Log.i(TAG, "Found system device-admin disable warning dialog")
                val cancelled = clickCancel(root)
                if (cancelled) {
                    Log.i(TAG, "Cancelled system device-admin disable warning dialog")
                } else {
                    // BACK usually dismisses AlertDialog as Cancel (does not deactivate).
                    Log.w(TAG, "Cancel click missed — pressing BACK + HOME")
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
                TamperGateController.onDisableRequested(service.applicationContext)
                leaveDangerousSettings(service)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryCancelSystemDisableDialog failed", e)
        } finally {
            roots.forEach { it.recycle() }
        }
        return false
    }

    private fun leaveDangerousSettings(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun clickCancel(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val label = buildString {
                    append(node.text?.toString().orEmpty())
                    append(' ')
                    append(node.contentDescription?.toString().orEmpty())
                }.trim()
                if (TamperSettingsHeuristics.isCancelLabel(label)) {
                    val target = when {
                        node.isClickable -> node
                        node.parent?.isClickable == true -> node.parent
                        else -> null
                    }
                    if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let {
                        queue.add(it)
                        toRecycle.add(it)
                    }
                }
            }
        } finally {
            toRecycle.forEach { it.recycle() }
        }
        return false
    }

    private fun treeLooksLikeOurDeviceAdminDeactivate(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
    ): Boolean {
        val ourLabel = service.getString(R.string.app_name).trim().lowercase()
        val ourPackage = service.packageName.lowercase()
        val text = collectText(root)
        return TamperSettingsHeuristics.looksLikeOurDeviceAdminDeactivatePage(
            combinedLowerText = text,
            ourAppLabelLower = ourLabel,
            ourPackageLower = ourPackage,
        )
    }

    private fun collectText(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                node.text?.let { sb.append(it).append(' ') }
                node.contentDescription?.let { sb.append(it).append(' ') }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let {
                        queue.add(it)
                        toRecycle.add(it)
                    }
                }
            }
        } finally {
            toRecycle.forEach { it.recycle() }
        }
        return sb.toString().lowercase()
    }
}
