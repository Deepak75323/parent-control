package com.childmonitor.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.childmonitor.MonitoringPrefs
import com.childmonitor.tamper.TamperGateController

/**
 * Blocks device-admin deactivation in system Settings until parent PIN or verification code
 * is accepted on [com.childmonitor.tamper.TamperGateActivity].
 */
object DeviceAdminTamperGuard {

    private const val TAG = "DeviceAdminTamperGuard"
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

    private val DEACTIVATE_TEXTS = listOf(
        "deactivate this device administrator",
        "deactivate this administrator",
        "deactivate administrator",
        "deactivate admin",
        "disable admin",
        "disable this admin",
        "remove admin",
        "remove device admin",
        "uninstall device admin",
    )

    private val ACCESSIBILITY_CONTEXT_TEXTS = listOf(
        "accessibility",
        "accessibility service",
        "downloaded services",
        "installed services",
        "无障碍",
    )

    fun tryBlockDeactivate(service: AccessibilityService, packageName: String?): Boolean {
        if (!TamperGateController.shouldBlockSettingsInteraction(service)) return false
        if (packageName.isNullOrBlank() || packageName !in SETTINGS_PACKAGES) return false

        val now = System.currentTimeMillis()
        if (now - lastBlockAt < 1500) return false
        if (now - lastScanAt < 600) return false
        lastScanAt = now

        val root = service.rootInActiveWindow ?: return false
        return try {
            if (!treeLooksLikeDeviceAdminDeactivate(root)) return false
            lastBlockAt = now
            Log.i(TAG, "Blocking device admin deactivate pkg=$packageName")
            TamperGateController.onDisableRequested(service.applicationContext)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            true
        } finally {
            root.recycle()
        }
    }

    private fun treeLooksLikeDeviceAdminDeactivate(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var sawDeviceAdminContext = false
        var sawAccessibilityContext = false
        var sawDeactivate = false

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
            for (label in DEACTIVATE_TEXTS) {
                if (text.contains(label)) {
                    sawDeactivate = true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) {
                node.recycle()
            }
        }

        // Accessibility detail pages also show our app name and an Off toggle — never block those.
        if (sawAccessibilityContext && !sawDeviceAdminContext) {
            return false
        }

        return sawDeviceAdminContext && sawDeactivate
    }
}
