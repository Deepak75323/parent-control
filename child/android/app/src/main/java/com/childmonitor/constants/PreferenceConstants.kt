package com.childmonitor.constants

/**
 * SharedPreferences file names and key names for the child app.
 * Prefer referencing these from [com.childmonitor.MonitoringPrefs] rather than literals.
 */
object PreferenceConstants {
    const val PREFS_NAME = "child_monitor_prefs"
    const val SECURE_PREFS_NAME = "child_monitor_secure_prefs"

    const val KEY_API_BASE = "api_base_url"
    const val KEY_PAIRED = "is_paired"
    const val KEY_TAMPER_UNLOCK_UNTIL = "tamper_unlock_until_ms"
    const val KEY_MEDIA_PROJECTION = "media_projection_granted"
    const val KEY_FCM_TOKEN = "fcm_token"
    const val KEY_DEVICE_OWNER = "device_owner_provisioned"
    const val KEY_KIOSK_ENABLED = "kiosk_enabled"
}
