package com.childmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Minimal foreground notification required by Android for FGS.
 *
 * Completely hiding it is not allowed on modern Android (the OS kills the service).
 * This builder minimizes child-visible chrome: IMPORTANCE_MIN channel, silent flags,
 * near-invisible icon, and zero-width title/text so MIUI does not fall back to "Clock Time".
 */
object SilentNotificationHelper {

    /**
     * Versioned channel id — Xiaomi caches channel settings; bump when stealth config changes
     * so IMPORTANCE_MIN applies on devices that already had the old channel.
     */
    const val CHANNEL_ID = "monitoring_silent_v3"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Drop obsolete channels that showed the old loud / named notification.
        listOf(
            com.childmonitor.constants.NotificationConstants.CHANNEL_MONITORING_SILENT,
            "monitoring_silent_v2",
        ).forEach { oldId ->
            try {
                nm.deleteNotificationChannel(oldId)
            } catch (_: Exception) {
            }
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.silent_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.silent_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(false)
            }
        }
        nm.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(context: Context): Notification {
        ensureChannel(context)
        // Intentionally no content intent — reduces shade interaction surface.
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.silent_notification_title))
            .setContentText(context.getString(R.string.silent_notification_text))
            .setSmallIcon(R.drawable.ic_stat_silent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)

        return builder.build()
    }

    fun notificationId(): Int = NOTIFICATION_ID
}
