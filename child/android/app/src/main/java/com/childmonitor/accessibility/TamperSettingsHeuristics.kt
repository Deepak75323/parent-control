package com.childmonitor.accessibility

/**
 * Pure heuristics for Settings screens that deactivate Device Admin / Accessibility.
 * Kept free of Android framework types so unit tests can cover OEM wording (MIUI, etc.).
 */
object TamperSettingsHeuristics {

    fun isSettingsPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName in SETTINGS_PACKAGES ||
            packageName.endsWith(".settings") ||
            packageName.contains("securitycenter") ||
            packageName.contains("permissionmanager") ||
            packageName.contains("safecenter") ||
            packageName.contains("systemmanager")
    }

    fun looksLikeOurDeviceAdminDeactivatePage(
        combinedLowerText: String,
        ourAppLabelLower: String,
        ourPackageLower: String,
    ): Boolean {
        if (combinedLowerText.isBlank()) return false
        val mentionsUs =
            combinedLowerText.contains(ourAppLabelLower) ||
                combinedLowerText.contains(ourPackageLower) ||
                combinedLowerText.contains("childdeviceadmin")
        if (!mentionsUs) return false

        // Accessibility detail pages also show our app name — never treat as device-admin.
        if (hasAccessibilityContext(combinedLowerText) && !hasDeviceAdminContext(combinedLowerText)) {
            return false
        }
        if (!hasDeviceAdminContext(combinedLowerText)) return false
        return hasDeactivateAction(combinedLowerText)
    }

    fun looksLikeOurAccessibilityDisablePage(
        combinedLowerText: String,
        ourAppLabelLower: String,
        ourPackageLower: String,
        hasToggle: Boolean,
    ): Boolean {
        if (combinedLowerText.isBlank()) return false
        val mentionsUs =
            combinedLowerText.contains(ourAppLabelLower) ||
                combinedLowerText.contains(ourPackageLower) ||
                combinedLowerText.contains("appblockingaccessibilityservice")
        if (!mentionsUs) return false
        if (hasDeviceAdminContext(combinedLowerText) && !hasAccessibilityContext(combinedLowerText)) {
            return false
        }
        // MIUI service detail pages often omit the word "accessibility" — app name + toggle is enough.
        if (hasAccessibilityContext(combinedLowerText)) {
            return hasToggle || hasDisableAction(combinedLowerText) || looksLikeServicesList(combinedLowerText)
        }
        return hasToggle
    }

    /**
     * True for the Accessibility → Downloaded apps screen even when service rows are missing
     * from the a11y tree (common on MIUI). Match on window title only — page body text also
     * contains "Downloaded apps" as a menu row on the parent Accessibility screen.
     */
    fun looksLikeAccessibilityDownloadedAppsSurface(windowTitleLower: String): Boolean {
        val title = windowTitleLower.trim()
        return title.contains("downloaded apps") ||
            title.contains("downloaded services") ||
            title.contains("installed services") ||
            title.contains("installed apps") ||
            title == "downloaded" ||
            title.startsWith("downloaded ")
    }

    /**
     * MIUI/AOSP accessibility services list ("Downloaded apps").
     *
     * Rows may show On/Off text *or* Switch widgets with no status words. Title text is
     * sometimes missing from the tree — then fall back to multiple On/Off statuses.
     */
    fun looksLikeOurAccessibilityServicesList(
        combinedLowerText: String,
        ourAppLabelLower: String,
        ourPackageLower: String,
        hasToggle: Boolean = false,
        windowTitleLower: String = "",
    ): Boolean {
        // Entire downloaded-services surface is a tamper path; MIUI often hides row text.
        if (looksLikeAccessibilityDownloadedAppsSurface(windowTitleLower)) {
            return true
        }
        if (combinedLowerText.isBlank()) return false
        val mentionsUs =
            combinedLowerText.contains(ourAppLabelLower) ||
                combinedLowerText.contains(ourPackageLower) ||
                combinedLowerText.contains("appblockingaccessibilityservice")
        if (!mentionsUs) return false
        if (hasDeviceAdminContext(combinedLowerText) && !hasAccessibilityContext(combinedLowerText)) {
            return false
        }
        if (looksLikeServicesList(combinedLowerText)) return true
        if (hasAccessibilityContext(combinedLowerText) && (hasOnOffStatus(combinedLowerText) || hasToggle)) {
            return true
        }
        // Title not in tree: several On/Off statuses + our app ⇒ services list.
        return countOnOffStatuses(combinedLowerText) >= 2
    }

    fun looksLikeServicesList(text: String): Boolean {
        return SERVICES_LIST_CONTEXT.any { text.contains(it) }
    }

    fun hasOnOffStatus(text: String): Boolean =
        ON_OFF_WORD.containsMatchIn(text)

    fun countOnOffStatuses(text: String): Int =
        ON_OFF_WORD.findAll(text).count()

    fun hasDeviceAdminContext(text: String): Boolean {
        return DEVICE_ADMIN_CONTEXT.any { text.contains(it) }
    }

    fun hasAccessibilityContext(text: String): Boolean {
        return ACCESSIBILITY_CONTEXT.any { text.contains(it) }
    }

    fun hasDeactivateAction(text: String): Boolean {
        return DEACTIVATE_ACTIONS.any { text.contains(it) }
    }

    fun hasDisableAction(text: String): Boolean {
        return DISABLE_ACTIONS.any { text.contains(it) }
    }

    /**
     * Android's DeviceAdminReceiver.onDisableRequested() warning dialog.
     * Tapping OK on that dialog always deactivates admin — we must Cancel it while locked.
     */
    fun looksLikeAdminDisableWarningDialog(combinedLowerText: String, warningLower: String): Boolean {
        if (combinedLowerText.isBlank()) return false
        if (warningLower.isNotBlank() && combinedLowerText.contains(warningLower)) return true
        return combinedLowerText.contains("parent verification is required") ||
            combinedLowerText.contains("before device protection can be turned off")
    }

    fun isCancelLabel(text: String): Boolean {
        val t = text.trim().lowercase()
        return t == "cancel" || t == "取消" || t == "रद्द करें" || t.startsWith("cancel")
    }

    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.miui.securitycenter",
        "com.miui.securityadd",
        "com.xiaomi.misettings",
        "com.samsung.android.settings",
        "com.samsung.android.lool",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.huawei.systemmanager",
        "com.vivo.permissionmanager",
    )

    private val DEVICE_ADMIN_CONTEXT = listOf(
        "device admin",
        "device admin app",
        "device admin apps",
        "device administrator",
        "administrator",
        "设备管理",
        "设备管理员",
        "डिवाइस एडमिन",
        "डिवाइस व्यवस्थापक",
    )

    private val ACCESSIBILITY_CONTEXT = listOf(
        "accessibility",
        "accessibility service",
        "downloaded services",
        "installed services",
        "downloaded apps",
        "downloaded app",
        "installed apps",
        "use installed apps",
        "无障碍",
        "已下载的应用",
        "已下载的应用服务",
        "सुलभता",
    )

    private val SERVICES_LIST_CONTEXT = listOf(
        "downloaded apps",
        "downloaded app",
        "downloaded services",
        "installed services",
        "installed apps",
        "use installed apps",
        "已下载的应用",
    )

    private val ON_OFF_WORD = Regex("""\b(on|off)\b""")

    private val DEACTIVATE_ACTIONS = listOf(
        "deactivate this device administrator",
        "deactivate this administrator",
        "deactivate administrator",
        "deactivate this device admin",
        "deactivate device admin",
        "deactivate admin",
        "deactivate",
        "disable admin",
        "disable this admin",
        "remove admin",
        "remove device admin",
        "uninstall device admin",
        "turn off",
        "停用",
        "关闭",
        "निष्क्रिय",
        "बंद करें",
    )

    private val DISABLE_ACTIONS = listOf(
        "turn off",
        "turn service off",
        "disable",
        "stop",
        "off",
        "关闭",
        "停用",
        "बंद करें",
    )
}
