package com.childmonitor.permissions

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.childmonitor.AppBlockingAccessibilityService
import com.childmonitor.TamperProtectionManager
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.capture.ScreenCaptureController

/**
 * Best-effort launcher for permission management screens (grant/revoke).
 * Tries multiple OEM-specific intents — do not gate on [Intent.resolveActivity] alone.
 */
object PermissionSettingsLauncher {

    private const val TAG = "PermissionSettingsLauncher"

    fun start(context: Context, activity: Activity?, intents: List<Intent>): Boolean {
        for (raw in intents) {
            try {
                val intent = Intent(raw)
                if (activity != null && !activity.isFinishing) {
                    activity.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                Log.i(TAG, "Opened settings action=${intent.action} component=${intent.component}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Settings intent failed action=${raw.action} component=${raw.component}", e)
            }
        }
        return false
    }

    fun openAccessibility(context: Context, activity: Activity?): Boolean {
        val service = ComponentName(context, AppBlockingAccessibilityService::class.java)
        val componentExtras = listOf(
            service.flattenToString(),
            service.flattenToShortString(),
            "${context.packageName}/${service.className}",
        )
        val intents = mutableListOf<Intent>()
        for (extra in componentExtras) {
            intents.add(
                Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                    putExtra("android.intent.extra.COMPONENT_NAME", extra)
                    putExtra(":settings:fragment_args_key", extra)
                },
            )
        }
        intents.add(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                putExtra(":settings:fragment_args_key", service.flattenToString())
            },
        )
        intents.add(
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.accessibility.MiuiAccessibilitySettingsActivity",
                ),
            ),
        )
        intents.add(
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.SubSettings",
                ),
            ).apply {
                putExtra(":settings:show_fragment", "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment")
                putExtra(":settings:fragment_args_key", service.flattenToString())
            },
        )
        intents.add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return start(context, activity, intents)
    }

    fun openDeviceAdmin(context: Context, activity: Activity?): Boolean {
        val intents = mutableListOf<Intent>()
        if (TamperProtectionManager.isDeviceAdminActive(context)) {
            intents.add(TamperProtectionManager.buildEnableAdminIntent(context, fromActivity = false))
        }
        intents.add(Intent("android.settings.DEVICE_ADMIN_SETTINGS"))
        intents.add(
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminSettings",
                ),
            ),
        )
        intents.add(
            Intent().setComponent(
                ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings"),
            ),
        )
        intents.add(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.DeviceAdminListActivity",
                ),
            ),
        )
        intents.add(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                ),
            ).apply {
                putExtra("extra_pkgname", context.packageName)
            },
        )
        intents.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
        )
        return start(context, activity, intents)
    }

    fun clearScreenCaptureConsent(context: Context) {
        ScreenCaptureController.releaseProjection()
        MediaProjectionTokenStore.clear(context)
    }
}
