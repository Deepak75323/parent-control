package com.childmonitor.capture

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger

/**
 * P1-05: Validate and recover [android.Manifest.permission.USE_FULL_SCREEN_INTENT].
 *
 * ## Platform behavior
 *
 * | API | Behavior |
 * |-----|----------|
 * | ≤ 33 (Android 13) | Permission not required — always [Status.NOT_REQUIRED]. |
 * | 34 (Android 14) | Special app access; must call [NotificationManager.canUseFullScreenIntent]. |
 * | 35 (Android 15) | Same check; Google tightened non-call FSI delivery — we still require the grant
 *                     and use CATEGORY_CALL notifications as the BAL fallback. |
 * | 36 (Android 16) | Same [canUseFullScreenIntent] gate; no new public API. |
 *
 * Manifest declaration alone is **not** enough on Android 14+ — the user (or OEM default)
 * can revoke the special access at any time. This helper:
 * 1. Validates the live grant state.
 * 2. Tracks ever-granted / revoked for setup and recovery UX.
 * 3. Opens the correct Settings screen (with app-details fallback).
 */
object FullScreenIntentHelper {

    private const val TAG = "FullScreenIntentHelper"
    private const val PREFS = "full_screen_intent_prefs"
    private const val KEY_EVER_GRANTED = "ever_granted"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_NEEDS_RECOVERY = "needs_recovery"
    private const val KEY_LAST_CHECKED_MS = "last_checked_ms"

    /** Live validation result for setup wizard / recovery. */
    enum class Status {
        /** Pre-Android 14 — FSI special access does not exist. */
        NOT_REQUIRED,
        /** Android 14+ and [NotificationManager.canUseFullScreenIntent] is true. */
        GRANTED,
        /** Android 14+ and the special access is denied or revoked. */
        DENIED,
    }

    /** @visibleForTesting — forces Status without NotificationManager (Robolectric gaps). */
    @Volatile
    internal var testStatusOverride: Status? = null

    fun isRequired(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * Authoritative grant check. Returns true when FSI is usable (or not required).
     * Persists ever-granted / revoked markers for recovery.
     */
    fun isGranted(context: Context): Boolean =
        validate(context) != Status.DENIED

    /**
     * Full validation with side-effect: updates prefs + logs when state changes
     * (fresh grant, revoke after upgrade, etc.).
     */
    fun validate(context: Context): Status {
        val app = context.applicationContext
        val override = testStatusOverride
        val status = when {
            override != null -> override
            !isRequired() -> Status.NOT_REQUIRED
            else -> {
                val nm = app.getSystemService(NotificationManager::class.java)
                if (nm != null && nm.canUseFullScreenIntent()) Status.GRANTED else Status.DENIED
            }
        }
        reconcilePrefs(app, status)
        return status
    }

    /** True when the permission was previously granted and is now denied (upgrade / revoke). */
    fun wasRevoked(context: Context): Boolean {
        if (!isRequired()) return false
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_EVER_GRANTED, false) &&
            validate(context) == Status.DENIED
    }

    fun needsRecovery(context: Context): Boolean {
        if (!isRequired()) return false
        return prefs(context).getBoolean(KEY_NEEDS_RECOVERY, false) || wasRevoked(context)
    }

    fun clearRecoveryFlag(context: Context) {
        prefs(context).edit().putBoolean(KEY_NEEDS_RECOVERY, false).apply()
    }

    /**
     * Called when a BAL fallback tries to use fullScreenIntent but the permission is denied.
     * Marks recovery needed and logs — does not open Settings mid-stream (disruptive).
     */
    fun onDeniedDuringUse(context: Context, reason: String) {
        if (!isRequired()) return
        val app = context.applicationContext
        prefs(app).edit()
            .putBoolean(KEY_NEEDS_RECOVERY, true)
            .putString(KEY_LAST_STATUS, Status.DENIED.name)
            .apply()
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.PERMISSION_FLOW,
            "full_screen_intent_denied_during_use",
            "reason=$reason api=${Build.VERSION.SDK_INT}",
        )
        Log.w(TAG, "USE_FULL_SCREEN_INTENT denied during use reason=$reason — recovery needed")
    }

    /**
     * Process / service start reconciliation — detects revoke after upgrade or Settings change.
     * @return current [Status]
     */
    fun validateOnProcessStart(context: Context): Status {
        val status = validate(context)
        if (status == Status.DENIED && prefs(context).getBoolean(KEY_EVER_GRANTED, false)) {
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.PERMISSION_FLOW,
                "full_screen_intent_revoked",
                "api=${Build.VERSION.SDK_INT} — re-grant via setup / dashboard",
            )
            Log.w(TAG, "USE_FULL_SCREEN_INTENT revoked after previous grant — needs recovery")
        } else {
            Log.i(TAG, "FSI validateOnProcessStart status=$status api=${Build.VERSION.SDK_INT}")
        }
        return status
    }

    fun openSettings(context: Context) {
        if (!isRequired()) return
        val app = context.applicationContext
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${app.packageName}"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            app.startActivity(intent)
            PcpFlowLogger.log(
                PcpFlowLogger.Category.PERMISSION_FLOW,
                "full_screen_intent_settings_opened",
                "action=MANAGE_APP_USE_FULL_SCREEN_INTENT",
            )
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT failed — app details fallback", e)
            try {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(fallback)
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.PERMISSION_FLOW,
                    "full_screen_intent_settings_fallback",
                    "error=${e.message}",
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open any FSI settings screen", e2)
                PcpFlowLogger.error(
                    PcpFlowLogger.Category.PERMISSION_FLOW,
                    "full_screen_intent_settings_failed",
                    e2,
                )
            }
        }
    }

    /** Debug / JS snapshot. */
    fun debugState(context: Context): Map<String, Any> {
        val status = validate(context)
        val prefs = prefs(context)
        return mapOf(
            "required" to isRequired(),
            "status" to status.name,
            "granted" to (status != Status.DENIED),
            "everGranted" to prefs.getBoolean(KEY_EVER_GRANTED, false),
            "needsRecovery" to needsRecovery(context),
            "wasRevoked" to wasRevoked(context),
            "api" to Build.VERSION.SDK_INT,
            "lastCheckedMs" to prefs.getLong(KEY_LAST_CHECKED_MS, 0L),
        )
    }

    /** @visibleForTesting */
    internal fun resetForTests(context: Context) {
        testStatusOverride = null
        prefs(context).edit().clear().commit()
    }

    private fun reconcilePrefs(context: Context, status: Status) {
        val prefs = prefs(context)
        val previous = prefs.getString(KEY_LAST_STATUS, null)
        val editor = prefs.edit()
            .putString(KEY_LAST_STATUS, status.name)
            .putLong(KEY_LAST_CHECKED_MS, System.currentTimeMillis())

        when (status) {
            Status.GRANTED -> {
                editor.putBoolean(KEY_EVER_GRANTED, true)
                editor.putBoolean(KEY_NEEDS_RECOVERY, false)
                if (previous != Status.GRANTED.name) {
                    PcpFlowLogger.log(
                        PcpFlowLogger.Category.PERMISSION_FLOW,
                        "full_screen_intent_granted",
                        "api=${Build.VERSION.SDK_INT}",
                    )
                }
            }
            Status.DENIED -> {
                if (prefs.getBoolean(KEY_EVER_GRANTED, false)) {
                    editor.putBoolean(KEY_NEEDS_RECOVERY, true)
                }
                if (previous != Status.DENIED.name) {
                    PcpFlowLogger.warn(
                        PcpFlowLogger.Category.PERMISSION_FLOW,
                        "full_screen_intent_denied",
                        "api=${Build.VERSION.SDK_INT} everGranted=${prefs.getBoolean(KEY_EVER_GRANTED, false)}",
                    )
                }
            }
            Status.NOT_REQUIRED -> {
                editor.putBoolean(KEY_NEEDS_RECOVERY, false)
            }
        }
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
