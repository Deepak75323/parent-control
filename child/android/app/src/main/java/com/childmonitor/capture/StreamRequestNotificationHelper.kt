package com.childmonitor.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.childmonitor.R

/**
 * Posts a high-priority notification whose fullScreenIntent is [StreamBootstrapActivity].
 *
 * Screen off/locked → system auto-starts the activity (legal BAL).
 * Screen on → heads-up; accessibility may auto-activate, or user taps.
 *
 * Important: do NOT use custom RemoteViews / invisible titles here — Xiaomi suppresses
 * fullScreenIntent and heads-up for those, which is why camera bootstrap never opened.
 */
object StreamRequestNotificationHelper {

    private const val TAG = "StreamRequestNotif"
    const val CHANNEL_ID = com.childmonitor.constants.NotificationConstants.CHANNEL_STREAM_REQUEST
    const val NOTIFICATION_ID = 5001

    fun post(context: Context, bootstrapIntent: Intent) {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — cannot post stream-request notification")
            return
        }

        val pending = buildBootstrapPendingIntent(context, bootstrapIntent)
        val title = context.getString(R.string.stream_request_notification_title)
        val text = context.getString(R.string.stream_request_notification_text)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_app_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(pending)
            .setSilent(false)
            .setOngoing(true)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setTimeoutAfter(60_000L)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val fsiStatus = FullScreenIntentHelper.validate(context)
        if (fsiStatus != FullScreenIntentHelper.Status.DENIED) {
            builder.setFullScreenIntent(pending, true)
        } else {
            FullScreenIntentHelper.onDeniedDuringUse(context, "stream_request_notification")
            Log.w(
                TAG,
                "USE_FULL_SCREEN_INTENT denied — posting heads-up only (no fullScreenIntent) " +
                    "api=${Build.VERSION.SDK_INT}",
            )
        }

        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, builder.build())
            Log.i(TAG, "stream-request notification posted title=$title fsi=$fsiStatus")
        } catch (e: Exception) {
            Log.w(TAG, "post failed: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun buildBootstrapPendingIntent(context: Context, bootstrapIntent: Intent): PendingIntent {
        // Do NOT pass ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED here.
        // On Android 16 (API 36) PendingIntent.getActivity(..., options) throws:
        // IllegalArgumentException: pendingIntentBackgroundActivityStartMode must not be set
        // when creating a PendingIntent. NotificationManager already allowlists FSI/content
        // PendingIntents for BAL when the notification is posted.
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, NOTIFICATION_ID, bootstrapIntent, flags)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf("stream_request", "stream_request_v2").forEach { oldId ->
            try {
                nm.deleteNotificationChannel(oldId)
            } catch (_: Exception) {
            }
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stream_request_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.stream_request_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(true)
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                Notification.AUDIO_ATTRIBUTES_DEFAULT,
            )
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(true)
            }
        }
        nm.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
