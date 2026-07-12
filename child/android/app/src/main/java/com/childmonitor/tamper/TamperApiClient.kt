package com.childmonitor.tamper

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONObject
import java.time.Instant
import com.childmonitor.constants.ApiPaths

object TamperApiClient {

    private const val TAG = "TamperApiClient"

    data class AttemptResult(
        val parentPinConfigured: Boolean,
        val message: String,
    )

    data class VerifyResult(
        val verified: Boolean,
        val unlockExpiresAt: Instant?,
    )

    fun reportDisableAttempt(context: Context, target: String? = null): AttemptResult? {
        val body = JSONObject().apply {
            if (!target.isNullOrBlank()) {
                put("target", target)
            }
        }.toString()
        val response = DeviceHttpClient.postJsonResponse(
            context,
            ApiPaths.DEVICE_TAMPER_ATTEMPT,
            body,
        ) ?: return null
        return parseAttempt(response)
    }

    fun reportAdminDisabled(context: Context) {
        DeviceHttpClient.postJson(context, ApiPaths.DEVICE_TAMPER_ADMIN_DISABLED, "{}")
    }

    fun verifyPin(context: Context, pin: String, target: String? = null): VerifyResult? {
        val json = JSONObject().put("pin", pin).apply {
            if (!target.isNullOrBlank()) {
                put("target", target)
            }
        }.toString()
        val body = DeviceHttpClient.postJsonResponse(
            context,
            ApiPaths.DEVICE_TAMPER_VERIFY,
            json,
        ) ?: return null
        return parseVerify(body)
    }

    fun verifyCode(context: Context, code: String, target: String? = null): VerifyResult? {
        val json = JSONObject().put("code", code).apply {
            if (!target.isNullOrBlank()) {
                put("target", target)
            }
        }.toString()
        val body = DeviceHttpClient.postJsonResponse(
            context,
            ApiPaths.DEVICE_TAMPER_VERIFY,
            json,
        ) ?: return null
        return parseVerify(body)
    }

    fun applyUnlock(context: Context, unlockExpiresAt: Instant?) {
        val untilMs = unlockExpiresAt?.toEpochMilli()
            ?: (System.currentTimeMillis() + 10 * 60_000L)
        MonitoringPrefs.setTamperUnlockUntilMs(context, untilMs)
    }

    private fun parseAttempt(body: String): AttemptResult? {
        return try {
            val data = JSONObject(body).optJSONObject("data") ?: return null
            AttemptResult(
                parentPinConfigured = data.optBoolean("parentPinConfigured"),
                message = data.optString("message", ""),
            )
        } catch (e: Exception) {
            Log.w(TAG, "parse attempt failed", e)
            null
        }
    }

    private fun parseVerify(body: String): VerifyResult? {
        return try {
            val data = JSONObject(body).optJSONObject("data") ?: return null
            val verified = data.optBoolean("verified")
            val expiresRaw = data.optString("unlockExpiresAt", null)
            val expiresAt = if (expiresRaw.isNullOrBlank()) null else Instant.parse(expiresRaw)
            VerifyResult(verified = verified, unlockExpiresAt = expiresAt)
        } catch (e: Exception) {
            Log.w(TAG, "parse verify failed", e)
            null
        }
    }
}
