package com.parentscaffold.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for parent JWT credentials (C1 remediation).
 */
object ParentSecureAuthStore {

    private const val PREFS = "parent_secure_auth_prefs"
    private const val KEY_AUTH_JSON = "auth_json"
    private const val LEGACY_ASYNC_KEY = "pcp_parent_auth_v1"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        encryptedPrefs?.let { return it }
        synchronized(this) {
            encryptedPrefs?.let { return it }
            val app = context.applicationContext
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                app,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            encryptedPrefs = created
            return created
        }
    }

    fun saveAuthJson(context: Context, json: String) {
        prefs(context).edit().putString(KEY_AUTH_JSON, json).commit()
    }

    fun loadAuthJson(context: Context): String? =
        prefs(context).getString(KEY_AUTH_JSON, null)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** One-time migration marker — legacy AsyncStorage key name for JS migration. */
    fun legacyAsyncStorageKey(): String = LEGACY_ASYNC_KEY
}
