package com.childmonitor.capture

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.childmonitor.MonitoringPrefs
import com.childmonitor.debug.PcpFlowLogger

/**
 * Holds MediaProjection consent for the current process.
 *
 * The system result [Intent] contains binders and cannot be written to disk
 * (Parcel.marshall throws). We keep a fresh in-memory copy for reuse until
 * the process dies or consent is revoked.
 *
 * P2-01: Consent *metadata* (flags / state / resultCode) is stored in
 * [EncryptedSharedPreferences]. The binder Intent stays in-memory only.
 */
object MediaProjectionTokenStore {

    private const val TAG = "MediaProjectionTokenStore"
    private const val PREFS = "media_projection_secure"
    private const val PREFS_ENCRYPTED = "media_projection_secure_enc"
    private const val KEY_RESULT_CODE = "result_code"
    private const val KEY_CONSENT_GRANTED = "consent_granted"
    private const val KEY_CONSENT_STATE = "consent_state"
    private const val KEY_EVER_GRANTED = "ever_granted"
    /** Legacy keys — cleared on save; URI tokens are invalid on MIUI. */
    private const val KEY_INTENT_URI = "intent_uri"
    private const val KEY_INTENT_PARCEL_B64 = "intent_parcel_b64"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    @Volatile
    private var inMemoryResultCode: Int? = null

    @Volatile
    private var inMemoryIntent: Intent? = null

    private fun prefs(context: Context): SharedPreferences {
        encryptedPrefs?.let { return it }
        synchronized(this) {
            encryptedPrefs?.let { return it }
            val app = context.applicationContext
            val created = createSecurePrefs(app)
            migrateFromPlainPrefs(app, created)
            encryptedPrefs = created
            return created
        }
    }

    /**
     * Prefer EncryptedSharedPreferences on device. Fall back to MODE_PRIVATE when the
     * Android Keystore is unavailable (JVM unit tests / broken OEM keystores) so consent
     * metadata still persists; production devices use encryption.
     */
    private fun createSecurePrefs(app: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                PREFS_ENCRYPTED,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable — falling back to private prefs", e)
            app.getSharedPreferences(PREFS_ENCRYPTED, Context.MODE_PRIVATE)
        }
    }

    /** One-time migrate plaintext `media_projection_secure` → encrypted store (P2-01). */
    private fun migrateFromPlainPrefs(context: Context, secure: SharedPreferences) {
        val plain = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (plain.all.isEmpty()) return
        Log.i(TAG, "P2-01 migrating MediaProjection consent metadata to EncryptedSharedPreferences")
        val editor = secure.edit()
        if (plain.contains(KEY_RESULT_CODE)) {
            editor.putInt(KEY_RESULT_CODE, plain.getInt(KEY_RESULT_CODE, 0))
        }
        if (plain.contains(KEY_CONSENT_GRANTED)) {
            editor.putBoolean(KEY_CONSENT_GRANTED, plain.getBoolean(KEY_CONSENT_GRANTED, false))
        }
        if (plain.contains(KEY_EVER_GRANTED)) {
            editor.putBoolean(KEY_EVER_GRANTED, plain.getBoolean(KEY_EVER_GRANTED, false))
        }
        plain.getString(KEY_CONSENT_STATE, null)?.let { editor.putString(KEY_CONSENT_STATE, it) }
        // Never migrate binder URI / parcel blobs — they are invalid after process death.
        editor.remove(KEY_INTENT_URI).remove(KEY_INTENT_PARCEL_B64)
        editor.commit()
        plain.edit().clear().commit()
    }

    fun save(context: Context, resultCode: Int, data: Intent) {
        val copy = Intent(data)
        inMemoryResultCode = resultCode
        inMemoryIntent = copy

        prefs(context).edit()
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
        val p = prefs(context)
        if (!p.contains(KEY_RESULT_CODE)) return null
        return p.getInt(KEY_RESULT_CODE, 0)
    }

    fun loadIntent(context: Context): Intent? {
        inMemoryIntent?.let {
            Log.d(TAG, "loadIntent — returning in-memory consent binder")
            return it
        }

        val p = prefs(context)
        val hadPersistedConsent = p.getBoolean(KEY_CONSENT_GRANTED, false) ||
            p.getBoolean(KEY_EVER_GRANTED, false) ||
            p.contains(KEY_RESULT_CODE)

        if (!hadPersistedConsent) {
            Log.d(TAG, "loadIntent — no persisted consent; in-memory binder unavailable")
            return null
        }

        val state = getConsentState(context)
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "loadIntent — in-memory binder lost after process death",
            "state=$state resultCode=${p.getInt(KEY_RESULT_CODE, 0)} " +
                "consentGranted=${p.getBoolean(KEY_CONSENT_GRANTED, false)}",
        )
        Log.e(
            TAG,
            "loadIntent: in-memory MediaProjection consent binder unavailable " +
                "(process death?) state=$state — triggering recovery notification",
        )

        when (state) {
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionConsentState.RECOVERY_FAILED,
            -> MediaProjectionLossHandler.onProjectionUnavailable(context, "loadIntent_process_death")
            MediaProjectionConsentState.RECOVERY_PENDING ->
                Log.i(TAG, "loadIntent — recovery already pending, skipping duplicate handler")
            MediaProjectionConsentState.RECOVERY_DEFERRED ->
                Log.i(TAG, "loadIntent — recovery deferred (WorkManager scheduled), skipping duplicate handler")
            MediaProjectionConsentState.NEVER_GRANTED ->
                Log.w(TAG, "loadIntent — unexpected NEVER_GRANTED with persisted consent markers")
            MediaProjectionConsentState.GRANTED_ACTIVE ->
                Log.w(TAG, "loadIntent — GRANTED_ACTIVE without in-memory binder; forcing reconsent")
        }

        return null
    }

    fun hasValidToken(context: Context): Boolean =
        getConsentState(context) == MediaProjectionConsentState.GRANTED_ACTIVE

    fun getConsentState(context: Context): MediaProjectionConsentState {
        val p = prefs(context)
        val persisted = MediaProjectionConsentState.fromPersisted(p.getString(KEY_CONSENT_STATE, null))
        val hasMemory = inMemoryIntent != null && inMemoryResultCode != null
        val hasActiveProjection = ScreenCaptureController.activeProjection() != null
        val legacyUri = p.contains(KEY_INTENT_URI) || p.contains(KEY_INTENT_PARCEL_B64)

        if (legacyUri) {
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "clearing legacy disk token — re-grant required once",
            )
            p.edit()
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
            persisted == MediaProjectionConsentState.RECOVERY_FAILED ||
            persisted == MediaProjectionConsentState.RECOVERY_DEFERRED
        ) {
            return persisted
        }

        val prefsFlag = p.getBoolean(KEY_CONSENT_GRANTED, false)
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

        if (p.getBoolean(KEY_EVER_GRANTED, false)) {
            return MediaProjectionConsentState.REQUIRES_RECONSENT
        }

        return MediaProjectionConsentState.NEVER_GRANTED
    }

    fun isProjectionLostAfterProcessRestart(context: Context): Boolean {
        val state = getConsentState(context)
        return state == MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH
    }

    fun needsScreenCaptureRecovery(context: Context): Boolean {
        if (hasValidToken(context)) return false
        return when (getConsentState(context)) {
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionConsentState.RECOVERY_DEFERRED,
            MediaProjectionConsentState.RECOVERY_FAILED -> true
            else -> false
        }
    }

    fun markRecoveryPending(context: Context) {
        persistState(context, MediaProjectionConsentState.RECOVERY_PENDING)
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "consent state", "RECOVERY_PENDING")
    }

    fun markRecoveryDeferred(context: Context, reason: String) {
        persistState(context, MediaProjectionConsentState.RECOVERY_DEFERRED)
        MonitoringPrefs.setMediaProjectionGranted(context, false)
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "recovery deferred — WorkManager retry scheduled",
            "reason=$reason state=RECOVERY_DEFERRED",
        )
        Log.w(TAG, "MediaProjection recovery deferred reason=$reason — WorkManager will retry")
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

    fun debugState(context: Context): Map<String, Any> {
        val p = prefs(context)
        val state = getConsentState(context)
        val hasMemory = inMemoryIntent != null && inMemoryResultCode != null
        val hasActiveProjection = ScreenCaptureController.activeProjection() != null
        return mapOf(
            "consentState" to state.name,
            "inMemory" to hasMemory,
            "resultCode" to (inMemoryResultCode ?: p.getInt(KEY_RESULT_CODE, 0)),
            "activeProjection" to hasActiveProjection,
            "prefsConsentFlag" to p.getBoolean(KEY_CONSENT_GRANTED, false),
            "everGranted" to p.getBoolean(KEY_EVER_GRANTED, false),
            "hasValidToken" to (state == MediaProjectionConsentState.GRANTED_ACTIVE),
            "legacyUriOnDisk" to p.contains(KEY_INTENT_URI),
            "encryptedPrefs" to true,
            "projectionLostAfterRestart" to isProjectionLostAfterProcessRestart(context),
            "needsScreenCaptureRecovery" to needsScreenCaptureRecovery(context),
            "blocksScreenShare" to state.blocksScreenShare(),
            "requiresParentMessage" to state.requiresParentReconsentMessage(),
            "isAwaitingDeferredRecovery" to state.isAwaitingDeferredRecovery(),
        )
    }

    fun clear(context: Context) {
        inMemoryResultCode = null
        inMemoryIntent = null
        prefs(context).edit().clear().apply()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        MonitoringPrefs.setMediaProjectionGranted(context, false)
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "consent cleared", "state=NEVER_GRANTED")
        Log.i(TAG, "cleared MediaProjection consent")
    }

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
        prefs(context).edit()
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
        prefs(context).edit()
            .putString(KEY_CONSENT_STATE, state.name)
            .apply()
    }

    /** @visibleForTesting */
    internal fun resetEncryptedPrefsForTests() {
        encryptedPrefs = null
        inMemoryResultCode = null
        inMemoryIntent = null
    }

    /**
     * @visibleForTesting Simulate process death: prefs say granted, binders gone.
     */
    internal fun seedPersistedGrantedWithoutBinderForTests(context: Context) {
        inMemoryResultCode = null
        inMemoryIntent = null
        prefs(context).edit()
            .putBoolean(KEY_CONSENT_GRANTED, true)
            .putBoolean(KEY_EVER_GRANTED, true)
            .putInt(KEY_RESULT_CODE, -1)
            .putString(KEY_CONSENT_STATE, MediaProjectionConsentState.GRANTED_ACTIVE.name)
            .commit()
    }
}
