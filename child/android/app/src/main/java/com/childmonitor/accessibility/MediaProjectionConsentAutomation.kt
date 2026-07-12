package com.childmonitor.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Sideload-only helper: auto-tap Android "Share screen" / "Start now" on the
 * MediaProjection consent dialog whenever it appears.
 *
 * Safety contract — the auto-click is only triggered when ALL of the following
 * are true for a given window:
 *   1. The window's package name is in [DIALOG_PACKAGES] (or the package name
 *      is absent, which can happen for certain system overlays).
 *   2. [looksLikeMediaProjectionContent] returns true, which requires BOTH a
 *      MediaProjection-specific title hint AND an approve-button hint to be
 *      present (AND logic, not OR).
 *
 * This prevents auto-clicking unrelated dialogs (camera permission, battery
 * optimisation, overlay permission, etc.) that happen to contain generic words
 * such as "Allow" or "OK".
 */
object MediaProjectionConsentAutomation {

    private const val TAG = "MediaProjAutoApprove"
    private var lastClickAt = 0L
    private var lastScanAt = 0L
    private val loggedUnknownPackages = mutableSetOf<String>()

    /**
     * Title-level hints that are unique to the system MediaProjection consent
     * dialog. Each string is matched with [String.contains] after lowercasing.
     *
     * Because [looksLikeMediaProjectionContent] requires BOTH a title match AND
     * an approve-button match (AND logic), it is safe to be inclusive here —
     * a lone approve button ("OK", "Allow") never triggers a click even if a
     * broad title hint fires.
     *
     * Intentionally excluded:
     *   - "allow display over other apps" → overlay (SYSTEM_ALERT_WINDOW) dialog
     *   - "capture screen" → appears in MIUI screen-capture settings pages as a
     *     menu item or page header (not a consent dialog)
     *
     * OEM coverage (see DIALOG_PACKAGES for host-package filtering):
     *   AOSP / Pixel   : "recording or casting", "start recording or casting",
     *                    "will have access to everything", "record your screen",
     *                    "capture your screen", "cast your screen",
     *                    "will start capturing"
     *   Samsung One UI  : "screenshot or screen record", "start screen recording",
     *                    "screen recording", "share your screen"
     *   Xiaomi MIUI /
     *   HyperOS         : "start capture", "capture everything", "screen recording",
     *                    "屏幕捕获", "录制屏幕权限", "开始录制屏幕",
     *                    "屏幕录制", "录制屏幕"
     *   vivo / iQOO     : "screen recording", "录制屏幕", "屏幕录制"
     *   OPPO / realme /
     *   OnePlus ColorOS : "screen recording", "share your screen",
     *                    "屏幕录制", "屏幕共享", "录制屏幕"
     *   Motorola        : AOSP titles (near-stock)
     *   Nothing         : AOSP titles (near-stock)
     */
    private val DIALOG_TITLE_HINTS = listOf(
        // ── AOSP / Google Pixel ─────────────────────────────────────────────
        // Android 14+: "Start recording or casting with <App>?"
        "start recording or casting",
        // Android 10-13: "Would you like to start recording or casting your screen?"
        "recording or casting",
        // Android 14+ body: "<App> will have access to everything on your screen."
        "will have access to everything",
        // Android 14+ action label also used as dialog title in some builds
        "share entire screen",
        // Android 13 AOSP / Motorola: "Allow <App> to record your screen?"
        "record your screen",
        // AOSP / Motorola variant: "Allow <App> to capture your screen?"
        "capture your screen",
        // Android 12- "Would you like to cast your screen with <App>?"
        "cast your screen",
        // AOSP variant found on some builds: "This app will start capturing …"
        "will start capturing",

        // ── Samsung One UI 5 / 6 / 7 ────────────────────────────────────────
        // One UI consent bottom-sheet title
        "screenshot or screen record",
        // One UI alternative title
        "start screen recording",
        // One UI 7 / newer Samsung builds: "Share your screen with <App>?"
        "share your screen",
        // Samsung and many other OEMs use this as the dialog header
        "screen recording",

        // ── Xiaomi / Redmi / POCO — MIUI 14 & HyperOS 1/2 ─────────────────
        // MIUI English consent dialog
        "start capture",
        "capture everything",

        // ── Chinese OEM consent titles (MIUI / vivo / OPPO / realme / Samsung)
        "屏幕捕获",     // "Screen Capture" — MIUI consent dialog header
        "录制屏幕权限", // "Screen Recording Permission" — MIUI / HyperOS header
        "开始录制屏幕", // "Start Recording Screen" — older MIUI
        "屏幕录制",     // "Screen Recording" (noun) — Samsung Chinese, vivo, OPPO
        "录制屏幕",     // "Record Screen" (verb) — vivo / OPPO / realme Chinese
        "屏幕共享",     // "Screen Sharing" — OPPO / OnePlus ColorOS Chinese
    )

    private val APPROVE_TEXTS = listOf(
        // ── High-confidence MediaProjection-specific labels (scored 8-10) ──
        "share screen",
        "start now",
        "start recording",
        "start sharing",
        "share entire screen",
        // ── Generic approve labels (scored 6) — safe because AND logic ──────
        "start",
        "allow",
        "accept",
        "agree",      // OPPO / ColorOS English approve button
        "ok",
        // ── MIUI / HyperOS / vivo / OPPO Chinese approve buttons ─────────
        "立即开始",   // "Start immediately"
        "开始",       // "Start"
        "允许",       // "Allow"
        "同意",       // "Agree"
        "确认",       // "Confirm"
        "确定",       // "OK / Confirm"
        "开始录制",   // "Start recording"
        "start capture",
        "capture",
    )

    /**
     * System MediaProjection consent UI only — not OEM settings / security centre screens.
     *
     * OEM host-package mapping:
     *   AOSP / Pixel / Motorola / Nothing : com.android.systemui,
     *                                        com.android.permissioncontroller,
     *                                        com.google.android.permissioncontroller
     *   Samsung                            : com.samsung.android.permissioncontroller
     *   Xiaomi / MIUI / HyperOS           : com.miui.systemui, com.miui.permcenter,
     *                                        com.miui.home, com.miui.securitycenter
     *   vivo / iQOO (Funtouch OS)         : com.vivo.permissioncontroller
     *   OPPO (ColorOS 13+)                 : com.coloros.permissioncontroller,
     *                                        com.oplus.permissioncontroller
     *   OPPO (older ColorOS / pre-12)      : com.oppo.permissioncontroller
     *   realme (realme UI / ColorOS base)  : com.realme.permissioncontroller,
     *                                        com.coloros.permissioncontroller
     *   OnePlus (OxygenOS 14+ / ColorOS)   : com.oneplus.permissioncontroller,
     *                                        com.oplus.permissioncontroller
     */
    private val DIALOG_PACKAGES = setOf(
        // ── Stock Android (AOSP / Pixel / Motorola / Nothing) ───────────────
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        // ── Samsung ─────────────────────────────────────────────────────────
        "com.samsung.android.permissioncontroller",
        // ── OnePlus / OPPO / realme (OxygenOS / ColorOS) ────────────────────
        "com.oneplus.permissioncontroller",
        "com.oplus.permissioncontroller",
        "com.coloros.permissioncontroller",
        "com.oppo.permissioncontroller",       // older OPPO pre-ColorOS 13
        "com.realme.permissioncontroller",     // realme UI custom override
        // ── vivo / iQOO (Funtouch OS / OriginOS) ────────────────────────────
        "com.vivo.permissioncontroller",
        // ── Xiaomi / Redmi / POCO — MIUI 14 & HyperOS ──────────────────────
        "com.miui.systemui",
        "com.miui.home",
        "com.miui.securitycenter",
        "com.miui.permcenter",
        // ── Self (used during DeviceOwner-mode onboarding) ──────────────────
        "com.childmonitor",
    )

    fun tryAutoApprove(service: AccessibilityService, packageName: String?): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickAt < 1200) return false
        if (now - lastScanAt < 400) return false
        lastScanAt = now

        val roots = collectDialogRoots(service)
        if (roots.isEmpty()) {
            Log.d(TAG, "No window roots to scan (pkg=$packageName)")
            return false
        }

        var clicked = false
        try {
            for (root in roots) {
                // Filter by the window's own package name (root.packageName), NOT the
                // event's packageName. The accessibility event source is the foreground app;
                // the dialog's host package may differ and is the only trustworthy signal.
                val rootPkg = root.packageName?.toString()
                if (!rootPkg.isNullOrBlank() && rootPkg !in DIALOG_PACKAGES) {
                    Log.d(TAG, "Skipping window from non-consent package=$rootPkg")
                    continue
                }

                // Always verify window content regardless of package.
                // A known package alone cannot distinguish a MediaProjection dialog from a
                // battery-optimisation, camera-permission, or overlay-permission dialog —
                // all of which may originate from com.android.systemui or a permission
                // controller. The content check (title AND approve-button, see AND logic in
                // looksLikeMediaProjectionContent) is the authoritative gate.
                if (!looksLikeMediaProjectionDialog(root)) continue

                if (clickApproveButton(root)) {
                    lastClickAt = now
                    clicked = true
                    Log.i(TAG, "Auto-approved MediaProjection dialog rootPkg=$rootPkg eventPkg=$packageName")
                    break
                } else {
                    Log.d(TAG, "MediaProjection dialog detected but approve button not found rootPkg=$rootPkg")
                }
            }
            if (!clicked && !packageName.isNullOrBlank() && !isKnownConsentPackage(packageName)) {
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

    private fun looksLikeMediaProjectionDialog(root: AccessibilityNodeInfo): Boolean =
        looksLikeMediaProjectionContent(collectNodeTexts(root))

    /**
     * Extracts all non-blank text snippets from the node tree rooted at [root].
     * Each snippet is the concatenation of [AccessibilityNodeInfo.text] and
     * [AccessibilityNodeInfo.contentDescription] for one node.
     */
    private fun collectNodeTexts(root: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val t = nodeText(node)
            if (t.isNotBlank()) texts.add(t)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) node.recycle()
        }
        return texts
    }

    /**
     * Pure content-based heuristic: returns **true** only when the window contains
     * **both** a MediaProjection-specific title phrase **and** an approve-button
     * label (AND logic).
     *
     * ### Why AND, not OR
     * With OR logic a dialog that contains only "Allow" (camera permission, battery
     * optimisation, etc.) would satisfy the condition and be auto-clicked. Requiring
     * both signals eliminates this class of false positive:
     *   - Camera permission dialog: has "Allow", no MediaProjection title → **false**
     *   - Battery optimisation dialog: has "OK", no MediaProjection title → **false**
     *   - Overlay permission dialog: has "Allow display over other apps", "Allow" →
     *     title is not in [DIALOG_TITLE_HINTS] → **false**
     *   - MediaProjection dialog: "Start recording or casting", "Start now" → **true**
     *
     * Exposed as `internal` so that unit tests can exercise it directly without an
     * Android device or [AccessibilityNodeInfo] mock infrastructure.
     */
    internal fun looksLikeMediaProjectionContent(allTexts: List<String>): Boolean {
        var sawTitle = false
        var sawApproveHint = false
        for (text in allTexts) {
            val lower = text.lowercase()
            if (lower.isBlank()) continue
            if (!sawTitle) {
                for (hint in DIALOG_TITLE_HINTS) {
                    if (lower.contains(hint)) {
                        sawTitle = true
                        break
                    }
                }
            }
            if (!sawApproveHint) {
                for (label in APPROVE_TEXTS) {
                    if (lower == label || (label.length >= 5 && lower.contains(label))) {
                        sawApproveHint = true
                        break
                    }
                }
            }
            // Short-circuit as soon as both signals are found.
            if (sawTitle && sawApproveHint) return true
        }
        // Require BOTH a title and an approve hint — neither alone is sufficient.
        return sawTitle && sawApproveHint
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
                    "start recording", "start sharing", "share entire screen",
                    "start capture", "开始录制" -> 7
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
                "Dialog pkg not in allowlist: $packageName " +
                    "(if MediaProjection dialog, add to DIALOG_PACKAGES)",
            )
        }
    }

    fun clearLoggedPackages() {
        loggedUnknownPackages.clear()
    }

    /**
     * P1-5: Returns true when [packageName] is a known MediaProjection consent dialog host.
     * Used by [com.childmonitor.AppBlockingAccessibilityService] to detect when a foreground
     * package change warrants restarting the auto-approve retry loop.
     */
    fun isKnownConsentPackage(packageName: String?): Boolean =
        !packageName.isNullOrBlank() && packageName in DIALOG_PACKAGES
}
