package com.childmonitor.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.childmonitor.R
import com.childmonitor.tamper.TamperGateController

/**
 * Blocks accessibility disable for this app in system Settings until parent PIN or verification
 * code is accepted on [com.childmonitor.tamper.TamperGateActivity].
 *
 * Weaker than Device Admin (no system OK/Cancel gate), so we react early on the services
 * list ("Downloaded apps") and try to flip our detail switch back On if it was turned off.
 *
 * MIUI often delivers WINDOW_* events before list rows inflate — a short scan sweep keeps
 * checking after the last event so we still catch Clock Time on the list.
 */
object AccessibilityTamperGuard {

    private const val TAG = "AccessibilityTamperGuard"
    private const val BLOCK_COOLDOWN_MS = 900L
    private const val SCAN_MIN_MS = 150L
    private const val SWITCH_SWEEP_INTERVAL_MS = 180L
    private const val SWITCH_SWEEP_COUNT = 10
    private const val PAGE_SWEEP_INTERVAL_MS = 220L
    private const val PAGE_SWEEP_COUNT = 16 // ~3.5s after entering Settings a11y surfaces

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pageSweepToken = Any()
    private val switchSweepToken = Any()
    private val gateRetryToken = Any()
    private var lastBlockAt = 0L
    private var lastScanAt = 0L
    private var switchSweepRemaining = 0
    private var pageSweepRemaining = 0

    fun tryBlockDisable(
        service: AccessibilityService,
        packageName: String?,
        eventType: Int,
    ): Boolean {
        if (!TamperGateController.shouldBlockAccessibilityDisable(service)) return false

        // Always try restore first — even mid-throttle — if detail switch was flipped Off.
        if (tryRestoreOurSwitchIfOff(service)) {
            startSwitchSweep(service)
            return true
        }

        val blocked = scanAndBlockIfDangerous(service, packageName, eventType, force = false)
        if (blocked) return true

        // Keep scanning briefly while Settings may still be inflating list rows.
        if (isOnSettingsAccessibilitySurface(service, packageName)) {
            startPageSweep(service)
        }
        return false
    }

    private fun startPageSweep(service: AccessibilityService) {
        if (pageSweepRemaining > 0) return
        pageSweepRemaining = PAGE_SWEEP_COUNT
        scheduleNextPageSweep(service)
    }

    private fun scheduleNextPageSweep(service: AccessibilityService) {
        if (pageSweepRemaining <= 0) return
        if (!TamperGateController.shouldBlockAccessibilityDisable(service)) {
            pageSweepRemaining = 0
            return
        }
        mainHandler.postDelayed({
            pageSweepRemaining--
            val blocked = scanAndBlockIfDangerous(service, null, eventType = -1, force = true)
            if (!blocked && pageSweepRemaining > 0) {
                scheduleNextPageSweep(service)
            } else if (blocked) {
                pageSweepRemaining = 0
            }
        }, pageSweepToken, PAGE_SWEEP_INTERVAL_MS)
    }

    private fun scanAndBlockIfDangerous(
        service: AccessibilityService,
        packageName: String?,
        eventType: Int,
        force: Boolean,
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!force) {
            if (now - lastBlockAt < BLOCK_COOLDOWN_MS) return false
            if (now - lastScanAt < SCAN_MIN_MS) return false
        } else if (now - lastBlockAt < BLOCK_COOLDOWN_MS) {
            return false
        }
        lastScanAt = now

        val roots = collectRoots(service, packageName)
        if (roots.isEmpty()) return false

        try {
            for ((root, windowTitle) in roots) {
                val page = analyzePage(service, root, windowTitle)
                if (!page.dangerous) {
                    if (page.textSnippet.contains("clock time") ||
                        page.textSnippet.contains("downloaded") ||
                        windowTitle.contains("downloaded")
                    ) {
                        Log.i(
                            TAG,
                            "a11y page not blocked list=${page.onServicesList} detail=${page.onDetailPage} " +
                                "toggle=${page.hasToggle} title=$windowTitle text=${page.textSnippet.take(160)}",
                        )
                    }
                    continue
                }

                lastBlockAt = now
                pageSweepRemaining = 0
                Log.i(
                    TAG,
                    "Blocking accessibility tamper surface list=${page.onServicesList} " +
                        "detail=${page.onDetailPage} toggle=${page.hasToggle} event=$eventType " +
                        "title=$windowTitle text=${page.textSnippet.take(120)}",
                )
                if (page.hasToggle) {
                    clickOurSwitchIfOff(root)
                }
                // Leave Settings with BACK only. HOME races the gate on MIUI and leaves
                // the user on the launcher with no tamper UI visible.
                leaveDangerousSettingsWithoutBuryingGate(service)
                showTamperGate(service)
                startSwitchSweep(service)
                return true
            }
        } finally {
            roots.forEach { it.root.recycle() }
        }
        return false
    }

    private fun isOnSettingsAccessibilitySurface(
        service: AccessibilityService,
        packageName: String?,
    ): Boolean {
        if (TamperSettingsHeuristics.isSettingsPackage(packageName)) {
            val active = service.rootInActiveWindow ?: return true
            try {
                val text = collectText(active)
                val title = activeWindowTitle(service)
                return TamperSettingsHeuristics.hasAccessibilityContext(text) ||
                    TamperSettingsHeuristics.looksLikeServicesList(text) ||
                    title.contains("downloaded") ||
                    title.contains("accessibility")
            } finally {
                active.recycle()
            }
        }
        val active = service.rootInActiveWindow ?: return false
        try {
            val pkg = active.packageName?.toString()
            if (!TamperSettingsHeuristics.isSettingsPackage(pkg)) return false
            val text = collectText(active)
            val title = activeWindowTitle(service)
            return TamperSettingsHeuristics.hasAccessibilityContext(text) ||
                TamperSettingsHeuristics.looksLikeServicesList(text) ||
                title.contains("downloaded") ||
                title.contains("accessibility")
        } finally {
            active.recycle()
        }
    }

    private fun activeWindowTitle(service: AccessibilityService): String {
        return try {
            service.windows
                ?.firstOrNull { it.isActive || it.isFocused }
                ?.title
                ?.toString()
                ?.lowercase()
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Pop Settings off the stack so the child cannot keep interacting with the toggle list.
     * Never HOME here — that buries [TamperGateActivity] under the launcher on HyperOS.
     */
    private fun leaveDangerousSettingsWithoutBuryingGate(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun showTamperGate(service: AccessibilityService) {
        // Launch from the a11y service context (BAL-friendly on MIUI), then retry shortly
        // so the gate ends up above Settings after BACK settles.
        TamperGateController.onAccessibilityDisableRequested(service)
        mainHandler.removeCallbacksAndMessages(gateRetryToken)
        mainHandler.postDelayed({
            TamperGateController.launchGate(
                service,
                TamperGateController.ACTION_ACCESSIBILITY,
                TamperGateController.ACTION_ACCESSIBILITY,
            )
        }, gateRetryToken, 150L)
    }

    private fun startSwitchSweep(service: AccessibilityService) {
        switchSweepRemaining = SWITCH_SWEEP_COUNT
        mainHandler.removeCallbacksAndMessages(pageSweepToken)
        mainHandler.removeCallbacksAndMessages(switchSweepToken)
        pageSweepRemaining = 0
        scheduleNextSweep(service)
    }

    private fun scheduleNextSweep(service: AccessibilityService) {
        if (switchSweepRemaining <= 0) return
        if (!TamperGateController.shouldBlockAccessibilityDisable(service)) {
            switchSweepRemaining = 0
            return
        }
        mainHandler.postDelayed({
            switchSweepRemaining--
            val restored = tryRestoreOurSwitchIfOff(service)
            if (restored || switchSweepRemaining > 0) {
                scheduleNextSweep(service)
            }
        }, switchSweepToken, SWITCH_SWEEP_INTERVAL_MS)
    }

    private fun tryRestoreOurSwitchIfOff(service: AccessibilityService): Boolean {
        val roots = collectRoots(service, null)
        if (roots.isEmpty()) return false
        try {
            for ((root, windowTitle) in roots) {
                val page = analyzePage(service, root, windowTitle)
                if (!page.onDetailPage || !page.hasToggle) continue
                if (clickOurSwitchIfOff(root)) {
                    Log.i(TAG, "Restored Clock Time accessibility switch to On")
                    leaveDangerousSettingsWithoutBuryingGate(service)
                    showTamperGate(service)
                    return true
                }
            }
        } finally {
            roots.forEach { it.root.recycle() }
        }
        return false
    }

    private data class RootRef(val root: AccessibilityNodeInfo, val windowTitle: String)

    private fun collectRoots(
        service: AccessibilityService,
        packageName: String?,
    ): List<RootRef> {
        val roots = mutableListOf<RootRef>()
        val windows = try {
            service.windows
        } catch (_: Exception) {
            null
        }
        if (windows != null) {
            for (window in windows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString()
                // Only Settings roots — never SystemUI (status bar text includes our
                // notification label "Clock Time" and caused false scans).
                if (TamperSettingsHeuristics.isSettingsPackage(pkg)) {
                    roots.add(RootRef(root, windowTitleOf(window)))
                } else {
                    root.recycle()
                }
            }
        }
        if (roots.isEmpty()) {
            val active = service.rootInActiveWindow
            if (active != null) {
                val pkg = active.packageName?.toString()
                if (TamperSettingsHeuristics.isSettingsPackage(pkg)) {
                    roots.add(RootRef(active, activeWindowTitle(service)))
                } else if (TamperSettingsHeuristics.isSettingsPackage(packageName)) {
                    // Event says Settings but active root is not yet Settings — keep trying.
                    active.recycle()
                } else {
                    active.recycle()
                }
            }
        }
        return roots
    }

    private fun windowTitleOf(window: AccessibilityWindowInfo): String =
        window.title?.toString()?.lowercase().orEmpty()

    private data class PageAnalysis(
        val onServicesList: Boolean,
        val onDetailPage: Boolean,
        val hasToggle: Boolean,
        val dangerous: Boolean,
        val textSnippet: String,
    )

    private fun analyzePage(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        windowTitleLower: String,
    ): PageAnalysis {
        val ourLabel = service.getString(R.string.app_name).trim().lowercase()
        val ourPackage = service.packageName.lowercase()
        val text = collectText(root)
        // Prefer framework text search — MIUI sometimes omits labels from a manual walk.
        val mentionsUsBySearch = nodeMentionsLabel(root, service.getString(R.string.app_name).trim())
        val combined =
            if (mentionsUsBySearch && !text.contains(ourLabel)) {
                "$text $ourLabel"
            } else {
                text
            }
        val hasToggle = findToggleNodes(root).isNotEmpty()
        val onList =
            TamperSettingsHeuristics.looksLikeOurAccessibilityServicesList(
                combinedLowerText = combined,
                ourAppLabelLower = ourLabel,
                ourPackageLower = ourPackage,
                hasToggle = hasToggle,
                windowTitleLower = windowTitleLower,
            )
        val onDetail =
            TamperSettingsHeuristics.looksLikeOurAccessibilityDisablePage(
                combinedLowerText = combined,
                ourAppLabelLower = ourLabel,
                ourPackageLower = ourPackage,
                hasToggle = hasToggle,
            )
        return PageAnalysis(
            onServicesList = onList,
            onDetailPage = onDetail && hasToggle,
            hasToggle = hasToggle,
            dangerous = onList || onDetail,
            textSnippet = combined,
        )
    }

    private fun nodeMentionsLabel(root: AccessibilityNodeInfo, label: String): Boolean {
        if (label.isBlank()) return false
        return try {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            val found = !nodes.isNullOrEmpty()
            nodes?.forEach { it.recycle() }
            found
        } catch (_: Exception) {
            false
        }
    }

    /**
     * If our accessibility detail switch is Off, click it to turn On.
     */
    private fun clickOurSwitchIfOff(root: AccessibilityNodeInfo): Boolean {
        val switches = findToggleNodes(root)
        try {
            for (node in switches) {
                if (node.isChecked) continue
                val target = when {
                    node.isClickable -> node
                    node.parent?.isClickable == true -> node.parent
                    else -> node
                }
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
        } finally {
            switches.forEach { it.recycle() }
        }
        return false
    }

    private fun findToggleNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val className = node.className?.toString().orEmpty()
            if (className.contains("Switch", ignoreCase = true) ||
                className.contains("CheckBox", ignoreCase = true) ||
                className.contains("Toggle", ignoreCase = true) ||
                className.contains("SlidingButton", ignoreCase = true)
            ) {
                found.add(AccessibilityNodeInfo.obtain(node))
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let {
                    queue.add(it)
                    toRecycle.add(it)
                }
            }
        }
        toRecycle.forEach { it.recycle() }
        return found
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
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    node.stateDescription?.let { sb.append(it).append(' ') }
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
        return sb.toString().lowercase()
    }
}
