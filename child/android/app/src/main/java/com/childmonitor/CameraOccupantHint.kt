package com.childmonitor

import android.content.Context
import android.content.pm.PackageManager

/** Foreground app hint when another process holds the camera. */
object CameraOccupantHint {

    data class Hint(val packageName: String?, val label: String?)

    fun current(context: Context): Hint {
        val pkg = ForegroundAppTracker.current()?.takeIf { it.isNotBlank() }
        if (pkg == null || pkg == context.packageName) {
            return Hint(packageName = null, label = null)
        }
        val label =
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
        return Hint(packageName = pkg, label = label)
    }

    /**
     * Wire-safe token for stream failure reasons (no spaces). Prefer human label.
     * Example: "Google Meet" → "Google_Meet"
     */
    fun encodeForFailureReason(hint: Hint): String? {
        val raw = hint.label?.takeIf { it.isNotBlank() } ?: hint.packageName ?: return null
        val encoded =
            raw.trim()
                .replace(' ', '_')
                .replace(Regex("[^A-Za-z0-9._-]"), "")
                .take(48)
        return encoded.takeIf { it.isNotEmpty() }
    }

    fun failureReason(hint: Hint): String {
        val encoded = encodeForFailureReason(hint)
        return if (encoded != null) {
            "CAMERA_IN_USE_BY_OTHER_APP:$encoded"
        } else {
            "CAMERA_IN_USE_BY_OTHER_APP"
        }
    }
}
