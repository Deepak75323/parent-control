package com.childmonitor

import android.content.ComponentName
import android.content.Context
import com.childmonitor.constants.PreferenceConstants

object MonitoringPrefs {
    private const val PREFS = PreferenceConstants.PREFS_NAME
    private const val KEY_API_BASE = PreferenceConstants.KEY_API_BASE
    private const val KEY_PAIRED = PreferenceConstants.KEY_PAIRED
    private const val KEY_LAST_USAGE_SYNC = "last_usage_sync_ms"
    private const val KEY_LAST_INSTALLED_APPS_SYNC = "last_installed_apps_sync_ms"
    private const val KEY_TAMPER_PROTECTION = "tamper_protection_enabled"
    private const val KEY_HIDE_LAUNCHER = "hide_launcher_enabled"
    private const val KEY_LAUNCHER_PKG = "launcher_component_package"
    private const val KEY_LAUNCHER_CLASS = "launcher_component_class"
    private const val KEY_DEVICE_OWNER = "device_owner_provisioned"
    private const val KEY_MEDIA_PROJECTION = "media_projection_granted"
    private const val KEY_KIOSK_ENABLED = "kiosk_enabled"
    private const val KEY_PARENT_DISPLAY_NAME = "parent_display_name"
    private const val KEY_SCREEN_AUTO_APPROVE_UNTIL = "screen_auto_approve_until_ms"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_INTEGRITY_TOKEN = "integrity_token_cached"
    private const val KEY_INTEGRITY_TOKEN_MS = "integrity_token_ms"
    private const val KEY_TAMPER_UNLOCK_UNTIL = "tamper_unlock_until_ms"
    private const val KEY_OFFLINE_PIN_FAILS_SINCE_LOCK = "offline_pin_fails_since_lock"
    private const val KEY_OFFLINE_PIN_LOCK_UNTIL = "offline_pin_lock_until_ms"
    private const val KEY_OFFLINE_PIN_LOCKOUT_LEVEL = "offline_pin_lockout_level"
    private const val KEY_OFFLINE_PIN_LIFETIME_FAILS = "offline_pin_lifetime_fails"
    private const val KEY_OFFLINE_PIN_PENDING_REPORT = "offline_pin_pending_report"
    // Legacy keys — cleared on migrate
    private const val KEY_OFFLINE_PIN_FAIL_COUNT = "offline_pin_fail_count"
    private const val KEY_OFFLINE_PIN_WINDOW_MS = "offline_pin_window_start_ms"
    private const val KEY_WIZARD_PERSISTENCE_JSON = "wizard_persistence_json_v2"
    private const val KEY_PERMISSION_SETUP_IN_PROGRESS = "permission_setup_in_progress"
    private const val KEY_SKIP_AUTO_RELOCK_UNTIL = "skip_auto_relock_until_ms"
    private const val KEY_DEVICE_ADMIN_SETUP_GRACE_UNTIL = "device_admin_setup_grace_until_ms"
    private const val KEY_ACCESSIBILITY_TAMPER_PROTECTION = "accessibility_tamper_protection_enabled"
    private const val KEY_LAST_UNCAUGHT_CRASH_AT = "last_uncaught_crash_at_ms"
    private const val KEY_LAST_UNCAUGHT_CRASH_SUMMARY = "last_uncaught_crash_summary"

    /** Arm Accessibility auto-tap for MediaProjection dialog (sideload child APK). */
    fun armScreenCaptureAutoApprove(context: Context, windowMs: Long = 45_000L) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SCREEN_AUTO_APPROVE_UNTIL, System.currentTimeMillis() + windowMs)
            .apply()
    }

    fun disarmScreenCaptureAutoApprove(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_SCREEN_AUTO_APPROVE_UNTIL)
            .apply()
    }

    fun isScreenCaptureAutoApproveArmed(context: Context): Boolean {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SCREEN_AUTO_APPROVE_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun saveSyncConfig(
        context: Context,
        apiBaseUrl: String,
        deviceToken: String,
        deviceHmacKey: String? = null,
        parentDisplayName: String? = null,
    ) {
        SecureCredentialStore.saveCredentials(context, deviceToken, deviceHmacKey)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_API_BASE, apiBaseUrl.trimEnd('/'))
            .putBoolean(KEY_PAIRED, true)
            .apply {
                if (!parentDisplayName.isNullOrBlank()) {
                    putString(KEY_PARENT_DISPLAY_NAME, parentDisplayName)
                }
            }
            .commit()
    }

    fun parentDisplayName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PARENT_DISPLAY_NAME, null)

    fun setParentDisplayName(context: Context, parentDisplayName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (parentDisplayName.isNullOrBlank()) {
                remove(KEY_PARENT_DISPLAY_NAME)
            } else {
                putString(KEY_PARENT_DISPLAY_NAME, parentDisplayName)
            }
        }.commit()
    }

    fun deviceHmacKey(context: Context): String? =
        SecureCredentialStore.deviceHmacKey(context)

    fun setKioskEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_KIOSK_ENABLED, enabled)
            .apply()
    }

    fun isKioskEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KIOSK_ENABLED, false)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        SecureCredentialStore.clear(context)
    }

    /** Clears pairing credentials only — keeps permissions and MediaProjection consent. */
    fun clearPairedCredentials(context: Context) {
        SecureCredentialStore.clear(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PARENT_DISPLAY_NAME)
            .putBoolean(KEY_PAIRED, false)
            .commit()
    }

    fun isPaired(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PAIRED, false)

    /** True while the JS permission wizard is on screen — blocks background screen-capture recovery. */
    fun isPermissionSetupInProgress(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSION_SETUP_IN_PROGRESS, false)

    fun setPermissionSetupInProgress(context: Context, inProgress: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PERMISSION_SETUP_IN_PROGRESS, inProgress)
            .apply()
    }

    /**
     * Suppresses device-admin / accessibility tamper gating after the user starts enabling
     * Device Admin (and again on [android.app.admin.DeviceAdminReceiver.onEnabled]).
     * Post-activate Settings UI still looks like "Deactivate" and would false-trigger TamperGate.
     */
    fun beginDeviceAdminSetupGrace(context: Context, durationMs: Long = 90_000L) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DEVICE_ADMIN_SETUP_GRACE_UNTIL, System.currentTimeMillis() + durationMs)
            .commit()
    }

    fun isDeviceAdminSetupGraceActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DEVICE_ADMIN_SETUP_GRACE_UNTIL, 0L) > System.currentTimeMillis()

    fun clearDeviceAdminSetupGrace(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DEVICE_ADMIN_SETUP_GRACE_UNTIL)
            .commit()
    }

    /**
     * Suppresses JS foreground re-lock (ClockLock / password) while a system permission dialog
     * is open or just closed. Persisted so a React remount after MediaProjection
     * consent does not bounce the user back to the lock screen.
     */
    fun setSkipAutoRelockUntilMs(context: Context, untilMs: Long) {
        // commit() — must land before MediaProjectionConsentActivity starts; apply() races
        // MIUI React remount and bounces the child back to the password lock screen.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SKIP_AUTO_RELOCK_UNTIL, untilMs)
            .commit()
    }

    fun isSkipAutoRelockActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SKIP_AUTO_RELOCK_UNTIL, 0L) > System.currentTimeMillis()

    fun clearSkipAutoRelock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_SKIP_AUTO_RELOCK_UNTIL)
            .commit()
    }

    fun apiBaseUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_BASE, null)

    fun updateApiBaseUrl(context: Context, apiBaseUrl: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_API_BASE, apiBaseUrl.trimEnd('/'))
            .commit()
    }

    fun deviceToken(context: Context): String? =
        SecureCredentialStore.deviceToken(context)

    fun lastUsageSyncMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_USAGE_SYNC, 0L)

    fun setLastUsageSyncMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_USAGE_SYNC, ms)
            .apply()
    }

    fun lastInstalledAppsSyncMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_INSTALLED_APPS_SYNC, 0L)

    fun setLastInstalledAppsSyncMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_INSTALLED_APPS_SYNC, ms)
            .apply()
    }

    fun setTamperProtectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TAMPER_PROTECTION, enabled)
            .apply()
    }

    fun isTamperProtectionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_TAMPER_PROTECTION, true)

    /** User-controlled — blocks turning off accessibility in Settings until parent PIN/code. */
    fun isAccessibilityTamperProtectionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCESSIBILITY_TAMPER_PROTECTION, false)

    fun setAccessibilityTamperProtectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACCESSIBILITY_TAMPER_PROTECTION, enabled)
            .apply()
    }

    fun setTamperUnlockUntilMs(context: Context, untilMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_TAMPER_UNLOCK_UNTIL, untilMs)
            .apply()
    }

    fun tamperUnlockUntilMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_TAMPER_UNLOCK_UNTIL, 0L)

    fun clearTamperUnlock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_TAMPER_UNLOCK_UNTIL)
            .apply()
    }

    fun isTamperUnlocked(context: Context): Boolean =
        tamperUnlockUntilMs(context) > System.currentTimeMillis()

    /** Offline PIN lockout — see [com.childmonitor.tamper.OfflinePinLockoutPolicy]. */
    fun offlinePinLockoutState(context: Context): com.childmonitor.tamper.OfflinePinLockoutPolicy.State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return com.childmonitor.tamper.OfflinePinLockoutPolicy.State(
            failsSinceLock = prefs.getInt(KEY_OFFLINE_PIN_FAILS_SINCE_LOCK, 0),
            lockUntilMs = prefs.getLong(KEY_OFFLINE_PIN_LOCK_UNTIL, 0L),
            lockoutLevel = prefs.getInt(KEY_OFFLINE_PIN_LOCKOUT_LEVEL, 0),
            lifetimeFails = prefs.getInt(KEY_OFFLINE_PIN_LIFETIME_FAILS, 0),
            pendingReportFails = prefs.getInt(KEY_OFFLINE_PIN_PENDING_REPORT, 0),
        )
    }

    fun saveOfflinePinLockoutState(
        context: Context,
        state: com.childmonitor.tamper.OfflinePinLockoutPolicy.State,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_OFFLINE_PIN_FAILS_SINCE_LOCK, state.failsSinceLock)
            .putLong(KEY_OFFLINE_PIN_LOCK_UNTIL, state.lockUntilMs)
            .putInt(KEY_OFFLINE_PIN_LOCKOUT_LEVEL, state.lockoutLevel)
            .putInt(KEY_OFFLINE_PIN_LIFETIME_FAILS, state.lifetimeFails)
            .putInt(KEY_OFFLINE_PIN_PENDING_REPORT, state.pendingReportFails)
            .remove(KEY_OFFLINE_PIN_FAIL_COUNT)
            .remove(KEY_OFFLINE_PIN_WINDOW_MS)
            .apply()
    }

    fun isOfflinePinRateLimited(context: Context): Boolean {
        val state = offlinePinLockoutState(context)
        return com.childmonitor.tamper.OfflinePinLockoutPolicy.isLocked(
            state,
            System.currentTimeMillis(),
        )
    }

    fun offlinePinLockRemainingMs(context: Context): Long {
        val state = offlinePinLockoutState(context)
        return com.childmonitor.tamper.OfflinePinLockoutPolicy.remainingLockMs(
            state,
            System.currentTimeMillis(),
        )
    }

    fun recordOfflinePinFailure(context: Context): com.childmonitor.tamper.OfflinePinLockoutPolicy.FailureResult {
        val now = System.currentTimeMillis()
        val result = com.childmonitor.tamper.OfflinePinLockoutPolicy.recordFailure(
            offlinePinLockoutState(context),
            now,
        )
        saveOfflinePinLockoutState(context, result.state)
        if (result.action ==
            com.childmonitor.tamper.OfflinePinLockoutPolicy.FailureAction.BUDGET_EXCEEDED
        ) {
            SecureCredentialStore.clearOfflineParentPin(context)
        }
        return result
    }

    fun clearOfflinePinFailures(context: Context) {
        val pending = offlinePinLockoutState(context).pendingReportFails
        saveOfflinePinLockoutState(
            context,
            com.childmonitor.tamper.OfflinePinLockoutPolicy.State(pendingReportFails = pending),
        )
    }

    fun consumeOfflinePinPendingReport(context: Context): Int {
        val state = offlinePinLockoutState(context)
        val pending = state.pendingReportFails
        if (pending > 0) {
            saveOfflinePinLockoutState(
                context,
                com.childmonitor.tamper.OfflinePinLockoutPolicy.consumePendingReport(state),
            )
        }
        return pending
    }

    fun setHideLauncherEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HIDE_LAUNCHER, enabled)
            .apply()
    }

    fun isHideLauncherEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_LAUNCHER, false)

    fun setLauncherComponent(context: Context, component: ComponentName) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAUNCHER_PKG, component.packageName)
            .putString(KEY_LAUNCHER_CLASS, component.className)
            .apply()
    }

    fun launcherComponent(context: Context): ComponentName? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pkg = prefs.getString(KEY_LAUNCHER_PKG, null) ?: return null
        val cls = prefs.getString(KEY_LAUNCHER_CLASS, null) ?: return null
        return ComponentName(pkg, cls)
    }

    fun setDeviceOwnerProvisioned(context: Context, provisioned: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DEVICE_OWNER, provisioned)
            .apply()
    }

    fun isDeviceOwnerProvisioned(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEVICE_OWNER, false)

    fun setMediaProjectionGranted(context: Context, granted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MEDIA_PROJECTION, granted)
            .apply()
    }

    fun isMediaProjectionGranted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MEDIA_PROJECTION, false)

    fun setFcmToken(context: Context, token: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (token.isNullOrBlank()) remove(KEY_FCM_TOKEN) else putString(KEY_FCM_TOKEN, token)
        }.apply()
    }

    fun fcmToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FCM_TOKEN, null)

    /** Cached Play Integrity token — refreshed per heartbeat window (under server nonce TTL). */
    fun cachedIntegrityToken(context: Context, purpose: String = "heartbeat"): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cacheKey = "${KEY_INTEGRITY_TOKEN}_$purpose"
        val cacheMsKey = "${KEY_INTEGRITY_TOKEN_MS}_$purpose"
        val cached = prefs.getString(cacheKey, null)
        val lastMs = prefs.getLong(cacheMsKey, 0L)
        val maxAgeMs = if (purpose == "pair") 0L else 240_000L
        if (!cached.isNullOrBlank() && maxAgeMs > 0 && System.currentTimeMillis() - lastMs < maxAgeMs) {
            return cached
        }
        val fresh = com.childmonitor.security.PlayIntegrityHelper.requestToken(context, purpose) ?: return cached
        prefs.edit()
            .putString(cacheKey, fresh)
            .putLong(cacheMsKey, System.currentTimeMillis())
            .apply()
        return fresh
    }

    fun getWizardPersistenceJson(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WIZARD_PERSISTENCE_JSON, null)

    fun setWizardPersistenceJson(context: Context, json: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (json.isNullOrBlank()) {
                remove(KEY_WIZARD_PERSISTENCE_JSON)
            } else {
                putString(KEY_WIZARD_PERSISTENCE_JSON, json)
            }
        }.commit()
    }

    fun recordUncaughtCrash(context: Context, error: Throwable) {
        val summary = "${error.javaClass.simpleName}: ${error.message ?: "unknown"}"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_UNCAUGHT_CRASH_AT, System.currentTimeMillis())
            .putString(KEY_LAST_UNCAUGHT_CRASH_SUMMARY, summary.take(200))
            .apply()
    }

    fun lastUncaughtCrashAtMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UNCAUGHT_CRASH_AT, 0L)

    fun lastUncaughtCrashSummary(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_UNCAUGHT_CRASH_SUMMARY, null)

    fun hasRecentUncaughtCrash(context: Context, maxAgeMs: Long): Boolean {
        val at = lastUncaughtCrashAtMs(context)
        return at > 0L && System.currentTimeMillis() - at <= maxAgeMs
    }
}
