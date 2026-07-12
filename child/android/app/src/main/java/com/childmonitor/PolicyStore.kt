package com.childmonitor

import android.content.Context
import org.json.JSONObject

object PolicyStore {

    private const val PREFS = "policy_store"
    private const val KEY_BUNDLE = "policy_bundle"
    private const val KEY_VERSION = "policy_version"

    fun saveBundle(context: Context, json: String, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BUNDLE, json)
            .putString(KEY_VERSION, version)
            .apply()
    }

    fun getBundleJson(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BUNDLE, null)

    fun getVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_VERSION, null)

    fun getBundle(context: Context): JSONObject? {
        val raw = getBundleJson(context) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }
}
