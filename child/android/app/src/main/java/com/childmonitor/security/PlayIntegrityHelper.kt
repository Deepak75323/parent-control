package com.childmonitor.security

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Requests a Play Integrity token for pair/heartbeat attestation when Play Services is available.
 * Nonce is bound to purpose + androidId + timestamp for replay resistance.
 */
object PlayIntegrityHelper {

    private const val TAG = "PlayIntegrityHelper"

    fun buildBoundNonce(context: Context, purpose: String): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val ts = System.currentTimeMillis()
        val raw = "$purpose:$androidId:$ts:${UUID.randomUUID()}"
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun requestToken(context: Context, purpose: String = "heartbeat", cloudProjectNumber: Long? = null): String? {
        return try {
            val manager = IntegrityManagerFactory.create(context.applicationContext)
            val nonce = buildBoundNonce(context, purpose)
            val builder = IntegrityTokenRequest.builder().setNonce(nonce)
            if (cloudProjectNumber != null && cloudProjectNumber > 0) {
                builder.setCloudProjectNumber(cloudProjectNumber)
            }
            val request = builder.build()
            val latch = CountDownLatch(1)
            val tokenRef = AtomicReference<String?>()
            manager.requestIntegrityToken(request)
                .addOnSuccessListener { response ->
                    tokenRef.set(response.token())
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Integrity token request failed: ${e.message}")
                    latch.countDown()
                }
            latch.await(15, TimeUnit.SECONDS)
            tokenRef.get()
        } catch (e: Exception) {
            Log.w(TAG, "Play Integrity unavailable: ${e.message}")
            null
        }
    }
}
