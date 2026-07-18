package com.childmonitor.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.accessibility.ScreenCaptureAutoApprove
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.debug.PermissionDebugLogger
import com.childmonitor.deviceowner.MediaProjectionConsentActivity
import com.childmonitor.sync.HeartbeatSyncManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Obtains MediaProjection consent only from allowed entry points
 * ([ConsentLaunchSource.SETUP], [ConsentLaunchSource.USER_GRANT], or
 * [ConsentLaunchSource.PARENT_SCREEN_SHARE]).
 *
 * Parent Screen Share requires Accessibility (auto-approve). Cast is not shown
 * when Accessibility is unavailable — only SETUP / USER_GRANT may launch for
 * a manual Start during guided grant.
 */
object MediaProjectionConsentHelper {

    private const val TAG = "MediaProjConsentHelper"
    private const val DEFAULT_TIMEOUT_MS = 45_000L

    private val consentLatch = AtomicReference<CountDownLatch?>(null)

    @JvmStatic
    fun hasConsent(context: Context): Boolean {
        val ok = MediaProjectionTokenStore.hasValidToken(context)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.PERMISSION_FLOW,
            "hasConsent",
            "granted=$ok ${MediaProjectionTokenStore.debugState(context)}",
        )
        return ok
    }

    /**
     * Warm an existing projection or launch Cast — only when [source] is an allowed entry.
     *
     * For [ConsentLaunchSource.PARENT_SCREEN_SHARE]: if Accessibility is not ready, does **not**
     * launch Cast (returns false). Caller must notify the parent.
     */
    @JvmStatic
    @JvmOverloads
    fun ensureConsent(
        context: Context,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        source: ConsentLaunchSource,
    ): Boolean {
        PermissionDebugLogger.enter(
            "MediaProjectionConsentHelper.ensureConsent",
            "timeoutMs=$timeoutMs source=$source",
        )
        if (ScreenCaptureController.hasLiveProjection()) {
            PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureConsent — live projection, skip dialog")
            HeartbeatSyncManager.syncProjectionRestored(context.applicationContext)
            PermissionDebugLogger.exit("MediaProjectionConsentHelper.ensureConsent", "live projection")
            return true
        }
        if (hasConsent(context)) {
            if (MediaProjectionSessionKeeper.ensureReady(context.applicationContext)) {
                PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureConsent — warmed stored consent")
                HeartbeatSyncManager.syncProjectionRestored(context.applicationContext)
                PermissionDebugLogger.exit("MediaProjectionConsentHelper.ensureConsent", "warmed")
                return true
            }
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.PERMISSION_FLOW,
                "ensureConsent — stored consent unusable, forcing Cast re-grant",
                "source=$source",
            )
            MediaProjectionTokenStore.invalidateSession(context.applicationContext)
        }

        if (source == ConsentLaunchSource.PARENT_SCREEN_SHARE &&
            !ScreenCaptureAutoApprove.isReady(context)
        ) {
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.PERMISSION_FLOW,
                "ensureConsent — blocked Cast launch: Accessibility not ready (parent Screen Share)",
            )
            Log.w(TAG, "Skipping Cast launch — Accessibility required for parent Screen Share")
            PermissionDebugLogger.exit("MediaProjectionConsentHelper.ensureConsent", "a11y_required")
            return false
        }

        // SETUP is only for guided setup / unpaired grant.
        // USER_GRANT = explicit "Enable live view" / Grant screen capture tap (always OK).
        if (source == ConsentLaunchSource.SETUP) {
            val setupOk = MonitoringPrefs.isPermissionSetupInProgress(context) ||
                !MonitoringPrefs.isPaired(context)
            if (!setupOk) {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.PERMISSION_FLOW,
                    "ensureConsent — blocked Cast from SETUP after pairing (use USER_GRANT or parent Start)",
                )
                Log.w(TAG, "Skipping Cast launch — device already paired; wait for parent Start or Enable live view")
                PermissionDebugLogger.exit("MediaProjectionConsentHelper.ensureConsent", "setup_not_active")
                return false
            }
        }

        consentLatch.get()?.let { existing ->
            PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureConsent — awaiting in-flight dialog")
            return try {
                val signaled = existing.await(timeoutMs, TimeUnit.MILLISECONDS)
                val granted = signaled && hasConsent(context)
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.PERMISSION_FLOW,
                    "ensureConsent — in-flight dialog finished",
                    "signaled=$signaled granted=$granted",
                )
                granted
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }

        val autoApproveReady = ScreenCaptureAutoApprove.isReady(context)
        if (autoApproveReady) {
            ScreenCaptureAutoApprove.armForConsent(context, timeoutMs)
        } else {
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.PERMISSION_FLOW,
                "ensureConsent — Accessibility not ready; launching Cast for manual Start (setup)",
                "source=$source",
            )
        }
        val latch = CountDownLatch(1)
        consentLatch.set(latch)
        try {
            PcpFlowLogger.log(
                PcpFlowLogger.Category.PERMISSION_FLOW,
                "ensureConsent — launching MediaProjectionConsentActivity",
                "timeoutMs=$timeoutMs source=$source autoApproveArmed=$autoApproveReady",
            )
            Log.i(
                TAG,
                "Requesting MediaProjection consent source=$source autoApprove=$autoApproveReady",
            )
            MonitoringPrefs.setSkipAutoRelockUntilMs(
                context.applicationContext,
                System.currentTimeMillis() + timeoutMs + 5_000L,
            )
            if (context is Activity) {
                context.startActivity(Intent(context, MediaProjectionConsentActivity::class.java))
            } else {
                MediaProjectionConsentActivity.startFromBackground(context.applicationContext)
            }
            val signaled = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            val granted = signaled && hasConsent(context)
            if (granted) {
                PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureConsent — SUCCESS")
                Log.i(TAG, "MediaProjection consent obtained source=$source")
                MonitoringPrefs.setSkipAutoRelockUntilMs(
                    context.applicationContext,
                    System.currentTimeMillis() + 20_000L,
                )
                HeartbeatSyncManager.syncProjectionRestored(context.applicationContext)
            } else {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.PERMISSION_FLOW,
                    "ensureConsent — FAILED",
                    "signaled=$signaled source=$source state=${MediaProjectionTokenStore.debugState(context)}",
                )
                Log.w(TAG, "MediaProjection consent not obtained (signaled=$signaled source=$source)")
            }
            return granted
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            PcpFlowLogger.error(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureConsent interrupted", e)
            return false
        } finally {
            consentLatch.compareAndSet(latch, null)
            ScreenCaptureAutoApprove.disarm(context)
            PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureConsent — auto-approve disarmed")
            PermissionDebugLogger.exit("MediaProjectionConsentHelper.ensureConsent")
        }
    }

    internal fun signalConsentComplete() {
        PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "signalConsentComplete — releasing waiters")
        consentLatch.get()?.countDown()
    }
}
