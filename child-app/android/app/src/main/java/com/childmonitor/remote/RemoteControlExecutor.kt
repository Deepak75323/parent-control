package com.childmonitor.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.childmonitor.AppBlockingAccessibilityService
import org.json.JSONObject

/**
 * Executes remote control commands via the active [AppBlockingAccessibilityService].
 * Requires {@code canPerformGestures="true"} in accessibility config.
 */
object RemoteControlExecutor {

    private const val TAG = "RemoteControlExecutor"

    /** Human-readable reason when remote control cannot run (for command ack). */
    fun unavailableReason(): String? =
        if (AppBlockingAccessibilityService.instance != null) null
        else "Accessibility is turned off on the child device. Open child app → Permissions → enable Accessibility."

    fun execute(payload: JSONObject?, commandType: String): Boolean {
        val service = AppBlockingAccessibilityService.instance
            ?: run {
                Log.w(TAG, "Accessibility service not connected")
                return false
            }
        val body = payload ?: JSONObject()
        return when (commandType) {
            "TAP" -> tap(service, body)
            "SWIPE" -> swipe(service, body)
            "GLOBAL_BACK" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "GLOBAL_HOME" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "TYPE_TEXT" -> typeText(service, body)
            "LAUNCH_APP" -> launchApp(service, body)
            else -> {
                Log.w(TAG, "Unknown remote command: $commandType")
                false
            }
        }
    }

    private fun tap(service: AccessibilityService, payload: JSONObject): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val x = payload.optInt("x", -1).toFloat()
        val y = payload.optInt("y", -1).toFloat()
        if (x < 0 || y < 0) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSync(service, gesture)
    }

    private fun swipe(service: AccessibilityService, payload: JSONObject): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val x1 = payload.optInt("x1", -1).toFloat()
        val y1 = payload.optInt("y1", -1).toFloat()
        val x2 = payload.optInt("x2", -1).toFloat()
        val y2 = payload.optInt("y2", -1).toFloat()
        val duration = payload.optLong("durationMs", 300L).coerceIn(100, 2000)
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) return false
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSync(service, gesture)
    }

    private fun dispatchGestureSync(service: AccessibilityService, gesture: GestureDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val latch = java.util.concurrent.CountDownLatch(1)
        var success = false
        service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    latch.countDown()
                }
            },
            null,
        )
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return success
    }

    private fun typeText(service: AccessibilityService, payload: JSONObject): Boolean {
        val text = payload.optString("text", "")
        if (text.isBlank()) return false
        val root = service.rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: run {
            root.recycle()
            return false
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        root.recycle()
        return ok
    }

    private fun launchApp(service: AccessibilityService, payload: JSONObject): Boolean {
        val pkg = payload.optString("packageName", "")
        if (pkg.isBlank()) return false
        val intent = service.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        return true
    }
}
