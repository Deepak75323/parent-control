package com.childmonitor.capture

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.childmonitor.AppBlockingAccessibilityService
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.oem.OemVendor

/**
 * Three-layer Background Activity Launch (BAL) fallback chain — required for
 * MIUI/HyperOS where direct startActivity() from a service/broadcast context is
 * silently blocked (no exception, no log).
 *
 * Layer 1 (accessibility): [AppBlockingAccessibilityService] holds a weaker
 *   foreground token that MIUI often honours for startActivity().
 * Layer 2 (direct): Works on stock Android when the process is not BAL-restricted.
 *   On OEMs that silently drop BAL (Xiaomi/OPPO/…), success of startActivity() is
 *   **not trusted** — we always continue to the notification fallback.
 * Layer 3 (notification): PRIORITY_MAX / CATEGORY_CALL + fullScreenIntent.
 *   Locked screen → system auto-starts; unlocked → heads-up (Accessibility can tap).
 *
 * Shared by [StreamBootstrapActivity] and MediaProjection consent re-launch.
 */
object BalSafeActivityLauncher {

    fun launch(context: Context, intent: Intent, tag: String) {
        if (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val oemDropsBal = oemSilentDropsBal()

        // Layer 1 — Accessibility context (best BAL exemption on MIUI).
        // On OEMs that silently drop BAL, treat a11y success as "attempted" only — still
        // post FSI so unlock/screen-off auto-starts and a11y can auto-tap the heads-up.
        if (AppBlockingAccessibilityService.tryStartActivity(intent)) {
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "$tag accessibility launch",
                "oem=${OemVendor.detect().id} trust=${!oemDropsBal}",
            )
            if (!oemDropsBal) {
                return
            }
        }

        // Layer 2 — Direct startActivity from app context.
        // On OEMs that silently drop BAL, never treat this as success — fall through to FSI.
        if (canLaunchDirectly(context) && !oemDropsBal) {
            try {
                context.startActivity(intent)
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "$tag direct launch",
                    "oem=${OemVendor.detect().id}",
                )
                return
            } catch (e: Exception) {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "$tag direct launch failed — trying fullScreenIntent",
                    "error=${e.message}",
                )
            }
        } else if (oemDropsBal) {
            try {
                context.startActivity(intent)
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "$tag direct launch attempted (OEM may drop) — continuing to FSI",
                    "oem=${OemVendor.detect().id}",
                )
            } catch (e: Exception) {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "$tag direct launch failed — continuing to FSI",
                    "error=${e.message}",
                )
            }
        }

        // Layer 3 — Full-screen intent / heads-up notification + a11y retry wake.
        postFullScreenFallback(context, intent, tag)
        scheduleAccessibilityRetries(intent, tag)
    }

    /**
     * Retry starting the bootstrap activity from the accessibility service while the
     * stream-request heads-up is visible (screen-on Xiaomi path).
     */
    private fun scheduleAccessibilityRetries(intent: Intent, tag: String) {
        val delaysMs = longArrayOf(400L, 1200L, 2500L, 4500L)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        delaysMs.forEach { delay ->
            handler.postDelayed({
                if (AppBlockingAccessibilityService.tryStartActivity(Intent(intent))) {
                    PcpFlowLogger.log(
                        PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                        "$tag accessibility retry launch",
                        "delayMs=$delay oem=${OemVendor.detect().id}",
                    )
                }
            }, delay)
        }
    }

    private fun postFullScreenFallback(context: Context, intent: Intent, tag: String) {
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "$tag BAL blocked — falling back to fullScreenIntent notification",
            "fsi=${FullScreenIntentHelper.validate(context).name} oem=${OemVendor.detect().id}",
        )
        if (!FullScreenIntentHelper.isGranted(context)) {
            FullScreenIntentHelper.onDeniedDuringUse(context, "bal_fallback_$tag")
        }
        StreamRequestNotificationHelper.post(context.applicationContext, intent)
    }

    /**
     * OEMs where context.startActivity() from a service often returns without error
     * but never brings an Activity to the foreground.
     */
    private fun oemSilentDropsBal(): Boolean =
        when (OemVendor.detect()) {
            OemVendor.XIAOMI,
            OemVendor.OPPO,
            OemVendor.VIVO,
            OemVendor.REALME,
            OemVendor.ONEPLUS,
            -> true
            else -> false
        }

    /**
     * Returns true when direct startActivity() is likely to succeed on stock Android.
     * Not authoritative on Xiaomi — [oemSilentDropsBal] overrides trust.
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
