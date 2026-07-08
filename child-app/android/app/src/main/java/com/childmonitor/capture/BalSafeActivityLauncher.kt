package com.childmonitor.capture

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.childmonitor.AppBlockingAccessibilityService
import com.childmonitor.debug.PcpFlowLogger

/**
 * Three-layer Background Activity Launch (BAL) fallback chain — required for
 * MIUI/HyperOS where direct startActivity() from a service/broadcast context is
 * silently blocked (no exception, no log).
 *
 * Layer 1 (direct): Works on stock Android, EMUI, and when the app has a recent
 *   foreground window.
 * Layer 2 (accessibility): [AppBlockingAccessibilityService] may hold a weaker
 *   foreground token that MIUI honours for startActivity().
 * Layer 3 (notification): Post a PRIORITY_MAX / CATEGORY_CALL notification with
 *   fullScreenIntent. The system auto-triggers the intent on a locked screen; on an
 *   active screen it shows as a heads-up peek that the accessibility service (or the
 *   user) can tap.
 *
 * Shared by [StreamBootstrapActivity] and the MediaProjection consent re-launch used
 * during background recovery — both need an activity to appear even though the app
 * has no visible UI at request time.
 */
object BalSafeActivityLauncher {

    fun launch(context: Context, intent: Intent, tag: String) {
        if (canLaunchDirectly(context)) {
            try {
                context.startActivity(intent)
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "$tag direct launch",
                    "",
                )
                return
            } catch (e: Exception) {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "$tag direct launch failed — trying accessibility",
                    "error=${e.message}",
                )
            }
        }

        if (AppBlockingAccessibilityService.tryStartActivity(intent)) {
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "$tag accessibility launch",
                "",
            )
            return
        }

        PcpFlowLogger.warn(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "$tag BAL blocked — falling back to fullScreenIntent notification",
            "",
        )
        StreamRequestNotificationHelper.post(context.applicationContext, intent)
    }

    /**
     * Returns true when direct startActivity() is likely to succeed.
     * Conservative: only allow on API < 29 or when the process is not restricted.
     */
    private fun canLaunchDirectly(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return try {
            val am = context.getSystemService(ActivityManager::class.java)
            !am.isBackgroundRestricted
        } catch (_: Exception) {
            true
        }
    }
}
