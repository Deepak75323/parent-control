package com.childmonitor

import android.content.ComponentName
import android.content.Context

object MonitoringPrefs {
    private const val PREFS = "child_monitor_prefs"
    private const val KEY_API_BASE = "api_base_url"
    private const val KEY_PAIRED = "is_paired"
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
            .getBoolean(KEY_TAMPER_PROTECTION, false)

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
}
