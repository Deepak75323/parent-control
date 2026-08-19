package com.childmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * When the launcher icon is hidden before pairing, monitoring FGS (and its notification) never
 * starts — this notification is the only way back into setup.
 */
object SetupAccessNotificationHelper {

    const val CHANNEL_ID = com.childmonitor.constants.NotificationConstants.CHANNEL_SETUP_ACCESS
    private const val NOTIFICATION_ID = 1002

    fun sync(context: Context) {
        val app = context.applicationContext
        if (TamperProtectionManager.isLauncherHidden(app) && !MonitoringPrefs.isPaired(app)) {
            show(app)
        } else {
            dismiss(app)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.setup_access_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.setup_access_channel_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun show(context: Context) {
        ensureChannel(context)
        val launchIntent = TamperProtectionManager.getLaunchIntent(context) ?: return
        val pending = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.setup_access_notification_title))
            .setContentText(context.getString(R.string.setup_access_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        SilentNotificationHelper.applyAppClockIcon(notification, context)
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification.build())
    }

    private fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
