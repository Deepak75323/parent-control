package com.childmonitor.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.childmonitor.R
import com.childmonitor.tamper.TamperGateController

/**
 * Blocks accessibility disable for this app in system Settings until parent PIN or verification
 * code is accepted on [com.childmonitor.tamper.TamperGateActivity].
 */
object AccessibilityTamperGuard {

    private const val TAG = "AccessibilityTamperGuard"
    private var lastBlockAt = 0L
    private var lastScanAt = 0L

    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.miui.securitycenter",
        "com.samsung.android.settings",
        "com.samsung.android.lool",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.huawei.systemmanager",
        "com.vivo.permissionmanager",
    )

    private val ACCESSIBILITY_CONTEXT_TEXTS = listOf(
        "accessibility",
        "accessibility service",
        "downloaded services",
        "installed services",
        "无障碍",
    )

    private val DISABLE_TEXTS = listOf(
        "turn off",
        "turn service off",
        "disable",
        "stop",
        "off",
        "关闭",
        "停用",
    )

    fun tryBlockDisable(
        service: AccessibilityService,
        packageName: String?,
        eventType: Int,
    ): Boolean {
        if (!TamperGateController.shouldBlockAccessibilityDisable(service)) return false
        if (packageName.isNullOrBlank() || packageName !in SETTINGS_PACKAGES) return false

        val now = System.currentTimeMillis()
        if (now - lastBlockAt < 1500) return false
        if (now - lastScanAt < 600) return false
        lastScanAt = now

        val root = service.rootInActiveWindow ?: return false
        return try {
            val page = analyzePage(service, root)
            if (!page.onOurAccessibilityPage) return false

            val shouldBlock = when (eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> page.hasToggle
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> page.hasToggle
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> page.hasDisableText
                else -> false
            }
            if (!shouldBlock) return false

            lastBlockAt = now
            Log.i(TAG, "Blocking accessibility disable pkg=$packageName event=$eventType")
            TamperGateController.onAccessibilityDisableRequested(service.applicationContext)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            true
        } finally {
            root.recycle()
        }
    }

    private data class PageAnalysis(
        val onOurAccessibilityPage: Boolean,
        val hasToggle: Boolean,
        val hasDisableText: Boolean,
    )

    private fun analyzePage(service: AccessibilityService, root: AccessibilityNodeInfo): PageAnalysis {
        val ourLabel = service.getString(R.string.app_name).trim().lowercase()
        val ourPackage = service.packageName.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var sawAccessibilityContext = false
        var sawDeviceAdminContext = false
        var sawOurService = false
        var hasToggle = false
        var hasDisableText = false

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = buildString {
                append(node.text?.toString().orEmpty())
                append(' ')
                append(node.contentDescription?.toString().orEmpty())
            }.trim().lowercase()

            if (text.contains("device admin") || text.contains("device administrator")) {
                sawDeviceAdminContext = true
            }
            for (label in ACCESSIBILITY_CONTEXT_TEXTS) {
                if (text.contains(label)) {
                    sawAccessibilityContext = true
                }
            }
            if (text.contains(ourLabel) || text.contains(ourPackage) ||
                text.contains("appblockingaccessibilityservice")
            ) {
                sawOurService = true
            }
            for (label in DISABLE_TEXTS) {
                if (text.contains(label)) {
                    hasDisableText = true
                }
            }
            val className = node.className?.toString().orEmpty()
            if (className.contains("Switch", ignoreCase = true) ||
                className.contains("CheckBox", ignoreCase = true) ||
                className.contains("Toggle", ignoreCase = true)
            ) {
                hasToggle = true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) {
                node.recycle()
            }
        }

        val onOurAccessibilityPage = sawAccessibilityContext && sawOurService && !sawDeviceAdminContext
        return PageAnalysis(
            onOurAccessibilityPage = onOurAccessibilityPage,
            hasToggle = hasToggle,
            hasDisableText = hasDisableText,
        )
    }
}
