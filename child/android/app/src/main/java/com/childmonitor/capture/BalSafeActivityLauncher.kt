package com.childmonitor.capture

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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

        val oemDrops = oemSilentDropsBal()
        val overlayGranted = Settings.canDrawOverlays(context)
        val normalDirectEligible = canLaunchDirectly(context)

        // FlashGet-shaped primary path: SYSTEM_ALERT_WINDOW gives Android a documented
        // BAL exemption. Launch the translucent bootstrap activity first, let onResume
        // promote the typed camera/mic/mediaProjection FGS, then finish immediately.
        if (overlayGranted &&
            shouldTrustDirectLaunch(
                overlayGranted = true,
                oemDropsBackgroundLaunches = oemDrops,
                normalDirectLaunchEligible = normalDirectEligible,
            )
        ) {
            try {
                context.startActivity(intent)
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "$tag overlay-authorized launch",
                    "oem=${OemVendor.detect().id}",
                )
                return
            } catch (e: Exception) {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "$tag overlay launch failed — trying fallbacks",
                    "error=${e.message}",
                )
            }
        }

        // Layer 1 — Accessibility context (best BAL exemption on MIUI).
        val a11yAttempted = AppBlockingAccessibilityService.tryStartActivity(intent)
        if (a11yAttempted) {
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "$tag accessibility launch",
                "oem=${OemVendor.detect().id} trust=${!oemDrops}",
            )
            // On Xiaomi/OPPO, a11y startActivity() often returns without error but never
            // brings the activity up — always also post FSI so camera bootstrap can proceed.
            if (!oemDrops) {
                return
            }
        }

        // Layer 2 — Direct startActivity from app context.
        // On OEMs that silently drop BAL, never treat this as success — fall through to FSI.
        if (!overlayGranted &&
            shouldTrustDirectLaunch(
                overlayGranted = false,
                oemDropsBackgroundLaunches = oemDrops,
                normalDirectLaunchEligible = normalDirectEligible,
            )
        ) {
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
        } else if (oemDrops) {
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

        // Layer 3 — Full-screen intent / heads-up notification.
        postFullScreenFallback(context, intent, tag)
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
        // Screen-on path: FSI becomes a heads-up — try to activate it via accessibility.
        AppBlockingAccessibilityService.armStreamRequestAutoActivate()
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

    @JvmStatic
    internal fun shouldTrustDirectLaunch(
        overlayGranted: Boolean,
        oemDropsBackgroundLaunches: Boolean,
        normalDirectLaunchEligible: Boolean,
    ): Boolean =
        overlayGranted || (normalDirectLaunchEligible && !oemDropsBackgroundLaunches)
}
