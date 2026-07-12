package com.childmonitor.fcm

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.sync.DeviceHttpClient

/**
 * F3 fix: resilient FCM token upload with retry on next heartbeat cycle.
 *
 * Problem: the previous implementation used a fire-and-forget Thread.  If the network
 * was unavailable at the moment the token changed (common during app install / update),
 * the upload silently failed and was never retried.  The server kept the old (invalid)
 * token, so FCM pushes failed indefinitely until the token rotated again.
 *
 * Solution: persist a "pending upload" flag in SharedPreferences.  The upload is
 * attempted immediately and on every subsequent heartbeat until the server acknowledges
 * receipt.  [FcmTokenRegistrar.ensureTokenOnHeartbeat] already calls [uploadIfPending]
 * so no new call sites are required.
 */
object FcmTokenUploadManager {

    private const val TAG = "FcmTokenUploadManager"
    private const val PREF_PENDING_TOKEN = "fcm_pending_upload_token"

    /**
     * Persist the token for retry and attempt an immediate upload.
     * Safe to call from any thread.
     */
    fun scheduleUpload(context: Context, token: String) {
        if (token.isBlank()) return
        // Persist the pending token so it survives process death.
        setPendingToken(context, token)
        Thread {
            tryUpload(context, token)
        }.start()
    }

    /**
     * Called on every heartbeat.  No-op if there is no pending token.
     * Clears the pending token only on confirmed server acknowledgement.
     */
    fun uploadIfPending(context: Context) {
        val token = getPendingToken(context) ?: return
        Thread {
            tryUpload(context, token)
        }.start()
    }

    private fun tryUpload(context: Context, token: String) {
        if (!MonitoringPrefs.isPaired(context)) return
        val ok = FcmTokenRegistrar.uploadTokenToServer(context, token)
        if (ok) {
            clearPendingToken(context)
            Log.i(TAG, "FCM token upload confirmed — pending flag cleared")
        } else {
            Log.w(TAG, "FCM token upload failed — will retry on next heartbeat")
        }
    }

    private fun setPendingToken(context: Context, token: String) {
        context.getSharedPreferences("child_monitor_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_PENDING_TOKEN, token)
            .apply()
    }

    private fun getPendingToken(context: Context): String? =
        context.getSharedPreferences("child_monitor_prefs", Context.MODE_PRIVATE)
            .getString(PREF_PENDING_TOKEN, null)
            ?.takeIf { it.isNotBlank() }

    private fun clearPendingToken(context: Context) {
        context.getSharedPreferences("child_monitor_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_PENDING_TOKEN)
            .apply()
    }
}
