package com.childmonitor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class InstalledAppEntry(
    val packageName: String,
    val appLabel: String,
    val appCategory: String,
)

object InstalledAppsReader {

    fun listLaunchableApps(context: Context): List<InstalledAppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }

        return matches
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                InstalledAppEntry(
                    packageName = pkg,
                    appLabel = label,
                    appCategory = AppCategoryResolver.resolve(context, pkg),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appLabel.lowercase() }
    }
}
