package com.childmonitor

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for pairing credentials. Non-sensitive prefs remain in [MonitoringPrefs].
 */
object SecureCredentialStore {

    private const val PREFS = "child_monitor_secure_prefs"
    private const val KEY_DEVICE_TOKEN = "device_token"
    private const val KEY_DEVICE_HMAC = "device_hmac_key"
    private const val KEY_OFFLINE_PIN_SALT = "offline_parent_pin_salt"
    private const val KEY_OFFLINE_PIN_HASH = "offline_parent_pin_hash"
    private const val KEY_OFFLINE_PIN_ITERATIONS = "offline_parent_pin_iterations"
    private const val KEY_OFFLINE_PIN_SYNCED_AT = "offline_parent_pin_synced_at_ms"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        encryptedPrefs?.let { return it }
        synchronized(this) {
            encryptedPrefs?.let { return it }
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            migrateFromPlainPrefs(context.applicationContext, created)
            encryptedPrefs = created
            return created
        }
    }

    private fun migrateFromPlainPrefs(context: Context, secure: SharedPreferences) {
        val plain = context.getSharedPreferences("child_monitor_prefs", Context.MODE_PRIVATE)
        val token = plain.getString("device_token", null)
        val hmac = plain.getString("device_hmac_key", null)
        if (token.isNullOrBlank() && hmac.isNullOrBlank()) return
        secure.edit().apply {
            if (!token.isNullOrBlank()) putString(KEY_DEVICE_TOKEN, token)
            if (!hmac.isNullOrBlank()) putString(KEY_DEVICE_HMAC, hmac)
        }.apply()
        plain.edit()
            .remove("device_token")
            .remove("device_hmac_key")
            .apply()
    }

    fun saveCredentials(context: Context, deviceToken: String, deviceHmacKey: String?) {
        prefs(context).edit().apply {
            putString(KEY_DEVICE_TOKEN, deviceToken)
            if (!deviceHmacKey.isNullOrBlank()) {
                putString(KEY_DEVICE_HMAC, deviceHmacKey)
            } else {
                remove(KEY_DEVICE_HMAC)
            }
        }.commit()
    }

    fun deviceToken(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_TOKEN, null)

    fun deviceHmacKey(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_HMAC, null)

    fun saveOfflineParentPin(
        context: Context,
        saltBase64: String,
        hashBase64: String,
        iterations: Int,
        syncedAtMs: Long = System.currentTimeMillis(),
    ) {
        if (saltBase64.isBlank() || hashBase64.isBlank() || iterations < 1) return
        prefs(context).edit()
            .putString(KEY_OFFLINE_PIN_SALT, saltBase64)
            .putString(KEY_OFFLINE_PIN_HASH, hashBase64)
            .putInt(KEY_OFFLINE_PIN_ITERATIONS, iterations)
            .putLong(KEY_OFFLINE_PIN_SYNCED_AT, syncedAtMs)
            .commit()
    }

    fun offlineParentPinMaterial(context: Context): Triple<String, String, Int>? {
        val p = prefs(context)
        val salt = p.getString(KEY_OFFLINE_PIN_SALT, null) ?: return null
        val hash = p.getString(KEY_OFFLINE_PIN_HASH, null) ?: return null
        val iterations = p.getInt(KEY_OFFLINE_PIN_ITERATIONS, 0)
        if (salt.isBlank() || hash.isBlank() || iterations < 1) return null
        return Triple(salt, hash, iterations)
    }

    fun offlineParentPinSyncedAtMs(context: Context): Long =
        prefs(context).getLong(KEY_OFFLINE_PIN_SYNCED_AT, 0L)

    fun clearOfflineParentPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_OFFLINE_PIN_SALT)
            .remove(KEY_OFFLINE_PIN_HASH)
            .remove(KEY_OFFLINE_PIN_ITERATIONS)
            .remove(KEY_OFFLINE_PIN_SYNCED_AT)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
