package com.childmonitor.fcm

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.sync.DeviceHttpClient
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object FcmTokenRegistrar {

    private const val TAG = "FcmTokenRegistrar"
    private const val TOKEN_FETCH_TIMEOUT_MS = 8_000L

    /** Best-effort async refresh — persists locally when Firebase returns. */
    fun refreshToken(context: Context) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        FcmWakeHandler.onNewToken(context.applicationContext, token)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "FCM token refresh failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not configured: ${e.message}")
        }
    }

    /**
     * Returns a token for heartbeat: cached value, or blocks briefly for Firebase.
     * Does not throw when Firebase is unavailable.
     */
    fun tokenForHeartbeat(context: Context): String? {
        MonitoringPrefs.fcmToken(context)?.takeIf { it.isNotBlank() }?.let { return it }
        return fetchTokenBlocking(context, TOKEN_FETCH_TIMEOUT_MS)
    }

    private fun fetchTokenBlocking(context: Context, timeoutMs: Long): String? {
        return try {
            val latch = CountDownLatch(1)
            val tokenRef = AtomicReference<String?>()
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        FcmWakeHandler.onNewToken(context.applicationContext, token)
                        tokenRef.set(token)
                    }
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "FCM token fetch failed: ${e.message}")
                    latch.countDown()
                }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            tokenRef.get()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not configured: ${e.message}")
            null
        }
    }

    /** Best-effort token refresh during boot recovery when cached token is missing. */
    fun ensureTokenOnHeartbeat(context: Context) {
        // F3 fix: retry any pending token upload first (covers the case where the
        // upload failed at install/update time due to transient network unavailability).
        FcmTokenUploadManager.uploadIfPending(context)
        if (!MonitoringPrefs.fcmToken(context).isNullOrBlank()) return
        refreshToken(context)
    }

    /** Push token to backend immediately (minimal heartbeat body). */
    fun uploadTokenToServer(context: Context, token: String): Boolean {
        if (!MonitoringPrefs.isPaired(context)) return false
        val body = JSONObject().put("fcmToken", token).toString()
        val ok = DeviceHttpClient.postJson(context, "/api/v1/device/heartbeat", body)
        if (ok) Log.i(TAG, "FCM token uploaded to server")
        else Log.w(TAG, "FCM token upload failed")
        return ok
    }
}
