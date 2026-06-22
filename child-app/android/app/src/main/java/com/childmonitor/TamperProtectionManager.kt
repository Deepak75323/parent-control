package com.childmonitor

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.childmonitor.MainActivity
import com.childmonitor.capture.StreamLifecycleLogger

object TamperProtectionManager {

    private fun launcherAliasComponent(context: Context): ComponentName =
        ComponentName(context.packageName, "${context.packageName}.LauncherAlias")

    private fun mainActivityComponent(context: Context): ComponentName =
        ComponentName(context, MainActivity::class.java)

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, ChildDeviceAdminReceiver::class.java)

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(context))
    }

    fun buildEnableAdminIntent(context: Context, fromActivity: Boolean = false): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_explanation),
            )
            if (!fromActivity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

    fun resolveLauncherComponent(context: Context): ComponentName? {
        val alias = launcherAliasComponent(context)
        if (hasLauncherComponent(context, alias)) {
            MonitoringPrefs.setLauncherComponent(context, alias)
            return alias
        }

        MonitoringPrefs.launcherComponent(context)?.let { stored ->
            if (hasLauncherComponent(context, stored)) return stored
        }

        // Include disabled launcher aliases (e.g. icon already hidden from a prior install).
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        intent.setPackage(context.packageName)
        val pm = context.packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        @Suppress("DEPRECATION")
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags))
        } else {
            pm.queryIntentActivities(intent, PackageManager.GET_DISABLED_COMPONENTS)
        }
        val activity = matches.firstOrNull()?.activityInfo ?: return null
        val component = ComponentName(activity.packageName, activity.name)
        MonitoringPrefs.setLauncherComponent(context, component)
        return component
    }

    private fun hasLauncherComponent(context: Context, component: ComponentName): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getActivityInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(PackageManager.GET_DISABLED_COMPONENTS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getActivityInfo(component, PackageManager.GET_DISABLED_COMPONENTS)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isLauncherComponentDisabled(context: Context, component: ComponentName): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(component)
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    }

    /** MainActivity must stay enabled for stream wake and headless capture. */
    private fun ensureMainActivityEnabled(context: Context) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            mainActivityComponent(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Re-enable MainActivity if an older build disabled it when hiding the icon. */
    private fun migrateLegacyMainActivityDisabled(context: Context) {
        val pm = context.packageManager
        val main = mainActivityComponent(context)
        val state = pm.getComponentEnabledSetting(main)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        ) {
            ensureMainActivityEnabled(context)
        }
    }

    fun isLauncherHidden(context: Context): Boolean {
        val alias = launcherAliasComponent(context)
        if (hasLauncherComponent(context, alias)) {
            return isLauncherComponentDisabled(context, alias)
        }
        return MonitoringPrefs.isHideLauncherEnabled(context)
    }

    /**
     * Hides the app icon from the launcher. Requires active device admin when [hidden] is true.
     */
    fun setHideLauncher(context: Context, hidden: Boolean): Boolean {
        if (hidden && !isDeviceAdminActive(context)) return false

        migrateLegacyMainActivityDisabled(context)
        ensureMainActivityEnabled(context)

        val component = resolveLauncherComponent(context) ?: return false
        val alreadyHidden = isLauncherComponentDisabled(context, component)
        if (hidden && alreadyHidden) {
            MonitoringPrefs.setHideLauncherEnabled(context, true)
            return true
        }
        if (!hidden && !alreadyHidden) {
            MonitoringPrefs.setHideLauncherEnabled(context, false)
            return true
        }

        val pm = context.packageManager
        val state = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        MonitoringPrefs.setHideLauncherEnabled(context, hidden)
        StreamLifecycleLogger.log(
            if (hidden) "LAUNCHER_HIDDEN" else "LAUNCHER_SHOWN",
            "component=${component.className}",
        )
        return true
    }

    fun applyStoredTamperSettings(context: Context) {
        migrateLegacyMainActivityDisabled(context)
        if (!isDeviceAdminActive(context)) return
        if (MonitoringPrefs.isHideLauncherEnabled(context)) {
            setHideLauncher(context, true)
        }
        blockUninstallIfDeviceOwner(context)
    }

    fun onDeviceAdminDisabled(context: Context) {
        setHideLauncher(context, false)
        MonitoringPrefs.setTamperProtectionEnabled(context, false)
        MonitoringPrefs.setHideLauncherEnabled(context, false)
    }

    /** Intent to open the RN app when the launcher icon is hidden (via foreground notification). */
    fun getLaunchIntent(context: Context): Intent? {
        return Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private fun blockUninstallIfDeviceOwner(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        dpm.setUninstallBlocked(adminComponent(context), context.packageName, true)
    }
}
