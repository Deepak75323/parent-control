package com.childmonitor.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.childmonitor.MonitoringPrefs

/**
 * Guarantees a presence heartbeat at least every [INTERVAL_MS] even when Android 15+
 * ends the dataSync FGS burst and WorkManager's 15-minute floor would miss the
 * backend offline cutoff ([DevicePresenceScheduler] ≈ 12 minutes).
 *
 * Uses [AlarmManager.setAndAllowWhileIdle] (Doze-friendly) and self-reschedules from
 * [PresenceHeartbeatReceiver] after each fire.
 */
object PresenceHeartbeatScheduler {

    private const val TAG = "PresenceHeartbeat"
    const val ACTION = "com.childmonitor.action.PRESENCE_HEARTBEAT"
    /** Keep under backend offline threshold (12 min) and nudge window (5 min) with margin. */
    const val INTERVAL_MS = 5 * 60 * 1000L
    private const val REQUEST_CODE = 0x5048 // 'PH'

    fun schedule(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) {
            cancel(context)
            return
        }
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent(app),
                )
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent(app))
            }
            Log.i(TAG, "HEARTBEAT_ALARM_SCHEDULED intervalMs=$INTERVAL_MS")
        } catch (e: Exception) {
            Log.e(TAG, "HEARTBEAT_ALARM_SCHEDULE_FAILED reason=${e.message}", e)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(pendingIntent(app))
            Log.i(TAG, "HEARTBEAT_ALARM_CANCELLED")
        } catch (e: Exception) {
            Log.w(TAG, "HEARTBEAT_ALARM_CANCEL_FAILED reason=${e.message}")
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PresenceHeartbeatReceiver::class.java).setAction(ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
