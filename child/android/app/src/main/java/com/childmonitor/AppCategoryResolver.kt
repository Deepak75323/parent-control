package com.childmonitor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

object AppCategoryResolver {

    fun resolve(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                pm.getApplicationInfo(packageName, 0)
            }
            mapCategory(info)
        } catch (_: Exception) {
            "OTHER"
        }
    }

    private fun mapCategory(info: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> "GAME"
                ApplicationInfo.CATEGORY_AUDIO,
                ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_IMAGE -> "ENTERTAINMENT"
                ApplicationInfo.CATEGORY_SOCIAL -> "SOCIAL"
                ApplicationInfo.CATEGORY_NEWS -> "NEWS"
                ApplicationInfo.CATEGORY_MAPS -> "MAPS"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "PRODUCTIVITY"
                else -> "OTHER"
            }
        }
        return "OTHER"
    }
}
