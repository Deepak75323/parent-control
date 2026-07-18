package com.childmonitor.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.childmonitor.PolicyEnforcementEngine

/**
 * Closes a blocked app more aggressively than Home alone.
 *
 * Pressing Home while YouTube (and similar apps) are playing often enters
 * Picture-in-Picture instead of ending the session. Sequence:
 * 1) tap close/dismiss affordances (PiP X)
 * 2) Back a few times to leave immersive playback
 * 3) show the block overlay
 * 4) Home
 * 5) killBackgroundProcesses (best-effort once no longer interactive)
 * 6) short follow-up sweeps for leftover PiP
 */
object BlockedAppCloser {

    private const val TAG = "BlockedAppCloser"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val CLOSE_LABELS = listOf(
        "close",
        "dismiss",
        "关闭",
        "बंद करें",
        "बंद",
    )

    fun enforce(
        service: AccessibilityService,
        packageName: String,
        reason: String,
    ) {
        Log.i(TAG, "enforce pkg=$packageName reason=$reason")
        dismissCloseAffordances(service, packageName)
        // Leave fullscreen / short-form UI before Home (Home → PiP on YouTube).
        repeat(3) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        PolicyEnforcementEngine.showBlockScreen(service.applicationContext, packageName, reason)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            killBackgroundBestEffort(service.applicationContext, packageName)
            dismissCloseAffordances(service, packageName)
        }, 400L)
        for (delay in longArrayOf(1_000L, 2_500L, 5_000L, 8_000L)) {
            mainHandler.postDelayed({
                if (!PolicyEnforcementEngine.isPackageBlocked(service.applicationContext, packageName)) {
                    return@postDelayed
                }
                dismissCloseAffordances(service, packageName)
                killBackgroundBestEffort(service.applicationContext, packageName)
            }, delay)
        }
    }

    fun dismissCloseAffordances(service: AccessibilityService, packageName: String): Boolean {
        var closed = false
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
                val pkg = root.packageName?.toString()
                if (pkg != null && pkg != packageName && pkg != "com.android.systemui") {
                    continue
                }
                if (clickCloseAffordance(root)) {
                    closed = true
                    Log.i(TAG, "clicked close affordance under pkg=${pkg ?: "?"}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "dismissCloseAffordances failed", e)
        } finally {
            roots.forEach { it.recycle() }
        }
        return closed
    }

    private fun clickCloseAffordance(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val text = buildString {
                    append(node.text?.toString().orEmpty())
                    append(' ')
                    append(node.contentDescription?.toString().orEmpty())
                }.trim().lowercase()
                val isClose = CLOSE_LABELS.any { label ->
                    text == label || text.startsWith("$label ") || text.endsWith(" $label")
                }
                if (isClose && (node.isClickable || node.isEnabled)) {
                    val target = if (node.isClickable) node else node.parent
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

    private fun killBackgroundBestEffort(context: Context, packageName: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.killBackgroundProcesses(packageName)
            Log.i(TAG, "killBackgroundProcesses requested pkg=$packageName")
        } catch (e: Exception) {
            Log.w(TAG, "killBackgroundProcesses failed pkg=$packageName", e)
        }
    }
}
