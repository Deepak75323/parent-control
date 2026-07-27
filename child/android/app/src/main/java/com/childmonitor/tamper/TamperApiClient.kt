package com.childmonitor.tamper

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.R
import com.childmonitor.SecureCredentialStore
import com.childmonitor.constants.ApiPaths
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONObject
import java.time.Instant

object TamperApiClient {

    private const val TAG = "TamperApiClient"
    private const val OFFLINE_UNLOCK_TTL_MS = 10 * 60_000L

    data class AttemptResult(
        val parentPinConfigured: Boolean,
        val message: String,
    )

    data class VerifyResult(
        val verified: Boolean,
        val unlockExpiresAt: Instant?,
        val usedOffline: Boolean = false,
    )

    /** Success or a user-facing error (resource id and/or server message). */
    sealed class AttemptOutcome {
        data class Success(val result: AttemptResult) : AttemptOutcome()
        data class Failure(
            val userMessageRes: Int,
            val userMessageOverride: String? = null,
        ) : AttemptOutcome()
    }

    sealed class VerifyOutcome {
        data class Success(val result: VerifyResult) : VerifyOutcome()
        data class Failure(
            val userMessageRes: Int,
            val userMessageOverride: String? = null,
        ) : VerifyOutcome()
    }

    fun reportDisableAttempt(context: Context, target: String? = null): AttemptResult? {
        return when (val outcome = reportDisableAttemptDetailed(context, target)) {
            is AttemptOutcome.Success -> outcome.result
            is AttemptOutcome.Failure -> null
        }
    }

    fun reportDisableAttemptDetailed(context: Context, target: String? = null): AttemptOutcome {
        val body = JSONObject().apply {
            if (!target.isNullOrBlank()) {
                put("target", target)
            }
        }.toString()
        return when (val http = DeviceHttpClient.postJsonDetailed(
            context,
            ApiPaths.DEVICE_TAMPER_ATTEMPT,
            body,
        )) {
            is DeviceHttpClient.HttpResult.Ok -> {
                val parsed = parseAttempt(http.body)
                cacheOfflinePinFromJson(context, http.body)
                if (parsed != null) {
                    AttemptOutcome.Success(parsed)
                } else {
                    Log.w(TAG, "tamper attempt parse failed body=${http.body.take(300)}")
                    AttemptOutcome.Failure(R.string.tamper_gate_try_again)
                }
            }
            is DeviceHttpClient.HttpResult.HttpError -> {
                Log.w(TAG, "tamper attempt HTTP ${http.code} err=${http.errorBody?.take(300)}")
                failureFromHttp(http.code, http.errorBody)
            }
            is DeviceHttpClient.HttpResult.NetworkFailure -> {
                Log.w(TAG, "tamper attempt network failure", http.cause)
                if (hasCachedOfflinePin(context)) {
                    AttemptOutcome.Success(
                        AttemptResult(
                            parentPinConfigured = true,
                            message = "offline",
                        ),
                    )
                } else {
                    AttemptOutcome.Failure(R.string.tamper_gate_no_internet)
                }
            }
            DeviceHttpClient.HttpResult.NoNetwork -> {
                Log.w(TAG, "tamper attempt no network")
                if (hasCachedOfflinePin(context)) {
                    AttemptOutcome.Success(
                        AttemptResult(
                            parentPinConfigured = true,
                            message = "offline",
                        ),
                    )
                } else {
                    AttemptOutcome.Failure(R.string.tamper_gate_no_internet)
                }
            }
            DeviceHttpClient.HttpResult.NotReady -> {
                Log.w(TAG, "tamper attempt not ready (missing api base or device token)")
                AttemptOutcome.Failure(R.string.tamper_gate_not_ready)
            }
        }
    }

    fun reportAdminDisabled(context: Context) {
        DeviceHttpClient.postJson(context, ApiPaths.DEVICE_TAMPER_ADMIN_DISABLED, "{}")
    }

    fun verifyPin(context: Context, pin: String, target: String? = null): VerifyResult? {
        return when (val outcome = verifyDetailed(context, pin = pin, code = null, target = target)) {
            is VerifyOutcome.Success -> outcome.result
            is VerifyOutcome.Failure -> null
        }
    }

    fun verifyCode(context: Context, code: String, target: String? = null): VerifyResult? {
        return when (val outcome = verifyDetailed(context, pin = null, code = code, target = target)) {
            is VerifyOutcome.Success -> outcome.result
            is VerifyOutcome.Failure -> null
        }
    }

    fun verifyDetailed(
        context: Context,
        pin: String?,
        code: String?,
        target: String? = null,
    ): VerifyOutcome {
        val json = JSONObject().apply {
            if (!pin.isNullOrBlank()) put("pin", pin)
            if (!code.isNullOrBlank()) put("code", code)
            if (!target.isNullOrBlank()) put("target", target)
        }.toString()
        return when (val http = DeviceHttpClient.postJsonDetailed(
            context,
            ApiPaths.DEVICE_TAMPER_VERIFY,
            json,
        )) {
            is DeviceHttpClient.HttpResult.Ok -> {
                val parsed = parseVerify(http.body)
                cacheOfflinePinFromJson(context, http.body)
                if (parsed != null) {
                    if (parsed.verified) {
                        MonitoringPrefs.clearOfflinePinFailures(context)
                    }
                    VerifyOutcome.Success(parsed)
                } else {
                    Log.w(TAG, "tamper verify parse failed body=${http.body.take(300)}")
                    VerifyOutcome.Failure(R.string.tamper_gate_try_again)
                }
            }
            is DeviceHttpClient.HttpResult.HttpError -> {
                Log.w(TAG, "tamper verify HTTP ${http.code} err=${http.errorBody?.take(300)}")
                // Backend unavailable (5xx) — allow offline fallback when cached verifier exists.
                if (http.code in 500..599 && !pin.isNullOrBlank()) {
                    return verifyOfflinePin(context, pin, code)
                }
                verifyFailureFromHttp(http.code, http.errorBody)
            }
            is DeviceHttpClient.HttpResult.NetworkFailure -> {
                Log.w(TAG, "tamper verify network failure — trying offline pin", http.cause)
                verifyOfflinePin(context, pin, code)
            }
            DeviceHttpClient.HttpResult.NoNetwork -> {
                Log.w(TAG, "tamper verify no network — trying offline pin")
                verifyOfflinePin(context, pin, code)
            }
            DeviceHttpClient.HttpResult.NotReady -> {
                Log.w(TAG, "tamper verify not ready — trying offline pin")
                verifyOfflinePin(context, pin, code)
            }
        }
    }

    /** Pull offline verifier while online (heartbeat / setup). */
    fun syncOfflinePinCredential(context: Context) {
        val body = DeviceHttpClient.get(context, ApiPaths.DEVICE_TAMPER_OFFLINE_PIN) ?: return
        cacheOfflinePinFromDataObject(context, JSONObject(body).optJSONObject("data"))
    }

    fun applyUnlock(context: Context, unlockExpiresAt: Instant?) {
        val untilMs = unlockExpiresAt?.toEpochMilli()
            ?: (System.currentTimeMillis() + OFFLINE_UNLOCK_TTL_MS)
        MonitoringPrefs.setTamperUnlockUntilMs(context, untilMs)
    }

    private fun verifyOfflinePin(
        context: Context,
        pin: String?,
        code: String?,
    ): VerifyOutcome {
        if (!code.isNullOrBlank()) {
            return VerifyOutcome.Failure(R.string.tamper_gate_offline_code_unavailable)
        }
        if (pin.isNullOrBlank()) {
            return VerifyOutcome.Failure(R.string.tamper_gate_no_internet)
        }
        val now = System.currentTimeMillis()
        val lockState = MonitoringPrefs.offlinePinLockoutState(context)
        if (OfflinePinLockoutPolicy.isLocked(lockState, now)) {
            val mins = ((OfflinePinLockoutPolicy.remainingLockMs(lockState, now) + 59_999) / 60_000)
                .coerceAtLeast(1)
            return VerifyOutcome.Failure(
                R.string.tamper_gate_rate_limited,
                "Too many wrong attempts. Try again in $mins minute(s).",
            )
        }
        val syncedAt = SecureCredentialStore.offlineParentPinSyncedAtMs(context)
        if (!OfflinePinLockoutPolicy.isVerifierFresh(syncedAt.takeIf { it > 0L }, now)) {
            return VerifyOutcome.Failure(R.string.tamper_gate_offline_pin_not_synced)
        }
        val material = loadCachedMaterial(context)
            ?: return VerifyOutcome.Failure(R.string.tamper_gate_offline_pin_not_synced)
        if (!OfflineParentPinVerifier.matches(pin, material)) {
            val failure = MonitoringPrefs.recordOfflinePinFailure(context)
            return when (failure.action) {
                OfflinePinLockoutPolicy.FailureAction.BUDGET_EXCEEDED ->
                    VerifyOutcome.Failure(
                        R.string.tamper_gate_offline_pin_not_synced,
                        "Too many offline attempts. Connect to the internet and try again.",
                    )
                OfflinePinLockoutPolicy.FailureAction.LOCKED -> {
                    val mins = ((failure.lockDurationMs + 59_999) / 60_000).coerceAtLeast(1)
                    VerifyOutcome.Failure(
                        R.string.tamper_gate_rate_limited,
                        "Too many wrong attempts. Locked for $mins minute(s).",
                    )
                }
                OfflinePinLockoutPolicy.FailureAction.NONE ->
                    VerifyOutcome.Failure(R.string.tamper_gate_wrong_secret)
            }
        }
        MonitoringPrefs.clearOfflinePinFailures(context)
        val expires = Instant.ofEpochMilli(System.currentTimeMillis() + OFFLINE_UNLOCK_TTL_MS)
        Log.i(TAG, "Offline parent password accepted")
        return VerifyOutcome.Success(
            VerifyResult(verified = true, unlockExpiresAt = expires, usedOffline = true),
        )
    }

    private fun hasCachedOfflinePin(context: Context): Boolean =
        SecureCredentialStore.offlineParentPinMaterial(context) != null

    private fun loadCachedMaterial(context: Context): OfflineParentPinVerifier.Material? {
        val triple = SecureCredentialStore.offlineParentPinMaterial(context) ?: return null
        return OfflineParentPinVerifier.Material(
            saltBase64 = triple.first,
            hashBase64 = triple.second,
            iterations = triple.third,
        )
    }

    private fun cacheOfflinePinFromJson(context: Context, body: String) {
        try {
            val data = JSONObject(body).optJSONObject("data") ?: return
            val offline = data.optJSONObject("offlinePin") ?: return
            cacheOfflinePinFromDataObject(context, offline)
        } catch (e: Exception) {
            Log.w(TAG, "cache offline pin failed", e)
        }
    }

    private fun cacheOfflinePinFromDataObject(context: Context, offline: JSONObject?) {
        if (offline == null) return
        val salt = offline.optString("saltBase64", "").trim()
        val hash = offline.optString("hashBase64", "").trim()
        val iterations = offline.optInt("iterations", 0)
        if (salt.isEmpty() || hash.isEmpty() || iterations < 1) return
        SecureCredentialStore.saveOfflineParentPin(context, salt, hash, iterations)
        Log.i(TAG, "Cached offline parent-password verifier")
    }

    private fun failureFromHttp(code: Int, errorBody: String?): AttemptOutcome.Failure {
        val serverMsg = extractServerMessage(errorBody)
        if (!serverMsg.isNullOrBlank()) {
            return AttemptOutcome.Failure(R.string.tamper_gate_try_again, serverMsg)
        }
        return AttemptOutcome.Failure(userMessageResForHttp(code))
    }

    private fun verifyFailureFromHttp(code: Int, errorBody: String?): VerifyOutcome.Failure {
        val serverMsg = extractServerMessage(errorBody)
        if (!serverMsg.isNullOrBlank()) {
            return VerifyOutcome.Failure(R.string.tamper_gate_try_again, serverMsg)
        }
        return VerifyOutcome.Failure(userMessageResForHttp(code))
    }

    private fun userMessageResForHttp(code: Int): Int =
        when {
            code == 401 || code == 403 -> R.string.tamper_gate_not_ready
            code == 429 -> R.string.tamper_gate_rate_limited
            code in 500..599 -> R.string.tamper_gate_try_again
            else -> R.string.tamper_gate_try_again
        }

    private fun extractServerMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val root = JSONObject(errorBody)
            val msg = root.optString("message").trim()
            if (msg.isNotEmpty() && !looksTechnical(msg)) msg else null
        } catch (_: Exception) {
            null
        }
    }

    private fun looksTechnical(message: String): Boolean =
        message.contains("Exception", ignoreCase = true) ||
            message.contains("SQL") ||
            message.contains("stack", ignoreCase = true) ||
            message.contains("HTTP ")

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
