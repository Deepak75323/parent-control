package com.childmonitor.capture

import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.debug.PcpFlowLogger

/**
 * Holds MediaProjection consent for the current process.
 *
 * The system result [Intent] contains binders and cannot be written to disk
 * (Parcel.marshall throws). We keep a fresh in-memory copy for reuse until
 * the process dies or consent is revoked.
 */
object MediaProjectionTokenStore {

    private const val TAG = "MediaProjectionTokenStore"
    private const val PREFS = "media_projection_secure"
    private const val KEY_RESULT_CODE = "result_code"
    private const val KEY_CONSENT_GRANTED = "consent_granted"
    private const val KEY_CONSENT_STATE = "consent_state"
    private const val KEY_EVER_GRANTED = "ever_granted"
    /** Legacy keys — cleared on save; URI tokens are invalid on MIUI. */
    private const val KEY_INTENT_URI = "intent_uri"
    private const val KEY_INTENT_PARCEL_B64 = "intent_parcel_b64"

    @Volatile
    private var inMemoryResultCode: Int? = null

    @Volatile
    private var inMemoryIntent: Intent? = null

    fun save(context: Context, resultCode: Int, data: Intent) {
        val copy = Intent(data)
        inMemoryResultCode = resultCode
        inMemoryIntent = copy

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_RESULT_CODE, resultCode)
            .putBoolean(KEY_CONSENT_GRANTED, true)
            .putBoolean(KEY_EVER_GRANTED, true)
            .putString(KEY_CONSENT_STATE, MediaProjectionConsentState.GRANTED_ACTIVE.name)
            .remove(KEY_INTENT_URI)
            .remove(KEY_INTENT_PARCEL_B64)
            .commit()

        MonitoringPrefs.setMediaProjectionGranted(context, true)

        ScreenCaptureController.onFreshConsentSaved()

        val extras = copy.extras?.keySet()?.joinToString(",") ?: "none"
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "consent saved in-memory",
            "resultCode=$resultCode state=GRANTED_ACTIVE extras=[$extras]",
        )
        Log.i(TAG, "saved MediaProjection consent in-memory resultCode=$resultCode extras=[$extras]")
    }

    fun loadResultCode(context: Context): Int? {
        inMemoryResultCode?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_RESULT_CODE)) return null
        return prefs.getInt(KEY_RESULT_CODE, 0)
    }

    fun loadIntent(context: Context): Intent? {
        inMemoryIntent?.let { return it }
        // Disk cannot hold binder-backed intents — session is lost after process death.
        return null
    }

    fun hasValidToken(context: Context): Boolean =
        getConsentState(context) == MediaProjectionConsentState.GRANTED_ACTIVE

    /**
     * Authoritative consent state — reconciles in-memory binders, active projection, and prefs.
     */
    fun getConsentState(context: Context): MediaProjectionConsentState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val persisted = MediaProjectionConsentState.fromPersisted(prefs.getString(KEY_CONSENT_STATE, null))
        val hasMemory = inMemoryIntent != null && inMemoryResultCode != null
        val hasActiveProjection = ScreenCaptureController.activeProjection() != null
        val legacyUri = prefs.contains(KEY_INTENT_URI) || prefs.contains(KEY_INTENT_PARCEL_B64)

        if (legacyUri) {
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "clearing legacy disk token — re-grant required once",
            )
            prefs.edit()
                .remove(KEY_INTENT_URI)
                .remove(KEY_INTENT_PARCEL_B64)
                .apply()
        }

        if (hasActiveProjection || hasMemory) {
            if (ScreenCaptureController.isConsentExhausted()) {
                return transitionToRequiresReconsent(context, "token_exhausted_runtime")
            }
            if (persisted != MediaProjectionConsentState.GRANTED_ACTIVE) {
                persistState(context, MediaProjectionConsentState.GRANTED_ACTIVE)
            }
            return MediaProjectionConsentState.GRANTED_ACTIVE
        }

        if (ScreenCaptureController.isConsentExhausted()) {
            return transitionToRequiresReconsent(context, "token_exhausted")
        }

        if (persisted == MediaProjectionConsentState.RECOVERY_PENDING ||
            persisted == MediaProjectionConsentState.RECOVERY_FAILED
        ) {
            return persisted
        }

        val prefsFlag = prefs.getBoolean(KEY_CONSENT_GRANTED, false)
        if (prefsFlag) {
            val lost = MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH
            if (persisted != lost) {
                persistState(context, lost)
            }
            return lost
        }

        if (persisted == MediaProjectionConsentState.REQUIRES_RECONSENT) {
            return persisted
        }

        if (prefs.getBoolean(KEY_EVER_GRANTED, false)) {
            return MediaProjectionConsentState.REQUIRES_RECONSENT
        }

        return MediaProjectionConsentState.NEVER_GRANTED
    }

    /** Prefs say consent was granted but in-memory binder token is gone (process death / restart). */
    fun isProjectionLostAfterProcessRestart(context: Context): Boolean {
        val state = getConsentState(context)
        return state == MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH
    }

    /**
     * True when screen capture was previously granted but must be re-obtained
     * (process death, MediaProjection.onStop after leaving for Settings, failed recovery).
     */
    fun needsScreenCaptureRecovery(context: Context): Boolean {
        if (hasValidToken(context)) return false
        return when (getConsentState(context)) {
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionConsentState.RECOVERY_FAILED -> true
            else -> false
        }
    }

    fun markRecoveryPending(context: Context) {
        persistState(context, MediaProjectionConsentState.RECOVERY_PENDING)
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "consent state", "RECOVERY_PENDING")
    }

    fun markRecoveryFailed(context: Context, reason: String) {
        persistState(context, MediaProjectionConsentState.RECOVERY_FAILED)
        MonitoringPrefs.setMediaProjectionGranted(context, false)
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "recovery failed",
            "reason=$reason state=RECOVERY_FAILED",
        )
    }

    fun markRequiresReconsent(context: Context, reason: String) {
        transitionToRequiresReconsent(context, reason)
    }

    fun projectionRestartUserMessage(): String =
        "Screen capture consent was lost after the app restarted. " +
            "Open the child app → Permissions → Grant screen capture again."

    fun logProjectionLostAfterProcessRestart(context: Context) {
        if (!isProjectionLostAfterProcessRestart(context)) return
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "Projection unavailable after process restart",
            projectionRestartUserMessage(),
        )
        Log.e(TAG, "Projection unavailable after process restart — re-grant screen capture")
    }

    /** Debug snapshot for JS logging. Does not mutate prefs. */
    fun debugState(context: Context): Map<String, Any> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = getConsentState(context)
        val hasMemory = inMemoryIntent != null && inMemoryResultCode != null
        val hasActiveProjection = ScreenCaptureController.activeProjection() != null
        return mapOf(
            "consentState" to state.name,
            "inMemory" to hasMemory,
            "resultCode" to (inMemoryResultCode ?: prefs.getInt(KEY_RESULT_CODE, 0)),
            "activeProjection" to hasActiveProjection,
            "prefsConsentFlag" to prefs.getBoolean(KEY_CONSENT_GRANTED, false),
            "everGranted" to prefs.getBoolean(KEY_EVER_GRANTED, false),
            "hasValidToken" to (state == MediaProjectionConsentState.GRANTED_ACTIVE),
            "legacyUriOnDisk" to prefs.contains(KEY_INTENT_URI),
            "projectionLostAfterRestart" to isProjectionLostAfterProcessRestart(context),
            "needsScreenCaptureRecovery" to needsScreenCaptureRecovery(context),
        )
    }

    fun clear(context: Context) {
        inMemoryResultCode = null
        inMemoryIntent = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        MonitoringPrefs.setMediaProjectionGranted(context, false)
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "consent cleared", "state=NEVER_GRANTED")
        Log.i(TAG, "cleared MediaProjection consent")
    }

    /**
     * Drop in-memory consent binders after MediaProjection.onStop — the system
     * token is consumed and getMediaProjection() cannot succeed again until re-grant.
     */
    fun invalidateSession(context: Context? = null) {
        inMemoryResultCode = null
        inMemoryIntent = null
        context?.let { transitionToRequiresReconsent(it, "session_invalidated") }
        CaptureFrameLogger.warn(
            "PROJECTION_SESSION_INVALIDATED",
            "MediaProjection.onStop — in-memory consent cleared, re-grant required",
        )
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "projection session invalidated",
            "re-grant screen capture required",
        )
        Log.w(TAG, "projection session invalidated — re-grant screen capture required")
    }

    private fun transitionToRequiresReconsent(context: Context, reason: String): MediaProjectionConsentState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CONSENT_GRANTED, false)
            .putString(KEY_CONSENT_STATE, MediaProjectionConsentState.REQUIRES_RECONSENT.name)
            .apply()
        MonitoringPrefs.setMediaProjectionGranted(context, false)
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "requires reconsent",
            "reason=$reason",
        )
        return MediaProjectionConsentState.REQUIRES_RECONSENT
    }

    private fun persistState(context: Context, state: MediaProjectionConsentState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CONSENT_STATE, state.name)
            .apply()
    }
}
