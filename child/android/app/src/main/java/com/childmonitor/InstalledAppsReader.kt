package com.childmonitor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream

data class InstalledAppEntry(
    val packageName: String,
    val appLabel: String,
    val appCategory: String,
    /** Small PNG as base64 (no data: prefix), optional. */
    val iconBase64: String? = null,
)

object InstalledAppsReader {

    private const val ICON_SIZE_PX = 48
    private const val MAX_ICON_BYTES = 24_000

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
                    iconBase64 = encodeAppIcon(pm, pkg),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appLabel.lowercase() }
    }

    private fun encodeAppIcon(pm: PackageManager, packageName: String): String? {
        return try {
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable, ICON_SIZE_PX)
            val stream = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 85, stream)) {
                return null
            }
            val bytes = stream.toByteArray()
            if (bytes.isEmpty() || bytes.size > MAX_ICON_BYTES) {
                return null
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
