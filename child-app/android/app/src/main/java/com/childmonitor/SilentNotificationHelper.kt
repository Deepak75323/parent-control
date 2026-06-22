package com.childmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Minimal, silent foreground notification required by Android for background services.
 * Parent actions (view location, policies, live stream) do not trigger child-visible alerts.
 */
object SilentNotificationHelper {

    const val CHANNEL_ID = "monitoring_silent"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun buildForegroundNotification(context: Context): Notification {
        ensureChannel(context)
        val launchIntent = TamperProtectionManager.getLaunchIntent(context)
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.silent_notification_title))
            .setContentText(context.getString(R.string.silent_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .apply {
                if (pending != null) setContentIntent(pending)
            }
            .build()
    }

    fun notificationId(): Int = NOTIFICATION_ID
}
