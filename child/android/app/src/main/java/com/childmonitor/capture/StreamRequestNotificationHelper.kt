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
 * Why this exists
 * ───────────────
 * Android 10 (API 29) introduced Background Activity Launch (BAL) restrictions.
 * MIUI/HyperOS silently drops context.startActivity() calls from a background
 * process even when a foreground service is running — there is no exception,
 * no crash, and no log.  FlashGet Kids and similar production parental-control
 * apps work around this by routing the launch through the notification system:
 *
 *   FCM / STOMP command received
 *       │
 *       ├─ direct startActivity() → MIUI drops silently
 *       │
 *       └─ post fullScreenIntent notification
 *             │
 *             ├─ Screen off / locked  → system auto-starts the activity (legal BAL)
 *             └─ Screen on           → heads-up peek appears; accessibility auto-taps
 *                                       if armed, or user taps manually
 *
 * The activity itself is completely transparent ([StreamBootstrapActivity]), so the
 * user sees nothing except a briefly visible notification heads-up that disappears
 * the moment [StreamBootstrapActivity] cancels it in onCreate().
 *
 * Android 14+ requires USE_FULL_SCREEN_INTENT (special app access) for
 * fullScreenIntent to function; fall back to a regular high-priority notification
 * that the accessibility service can auto-tap.
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

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, bootstrapIntent, flags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.silent_notification_title))
            .setContentText(context.getString(R.string.silent_notification_text))
            // PRIORITY_MAX causes the system to auto-start fullScreenIntent when the
            // screen is off, and show a heads-up peek when the screen is on.
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // CATEGORY_CALL is the only category MIUI/HyperOS treats with guaranteed
            // full-screen-intent delivery even under strict background restrictions.
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(pending)
            .setSilent(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .setShowWhen(false)

        // fullScreenIntent: system starts the activity automatically on lock/sleep screen
        // without user interaction.  On Android 14+, USE_FULL_SCREEN_INTENT must be validated
        // via NotificationManager.canUseFullScreenIntent() — not just the manifest permission.
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
            Log.i(TAG, "stream-request notification posted")
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

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stream_request_channel_name),
            // IMPORTANCE_HIGH is required for heads-up (peek) display when screen is on.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.stream_request_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
