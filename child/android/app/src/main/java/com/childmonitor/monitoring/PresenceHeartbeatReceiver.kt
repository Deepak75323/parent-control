package com.childmonitor.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.childmonitor.MonitoringPrefs
import com.childmonitor.sync.HeartbeatSyncManager
import com.childmonitor.sync.NativeStompOwner

/**
 * Alarm-driven presence check-in. Keeps [lastSeenAt] fresh without relying solely on
 * WorkManager's ≥15 min periodic floor or FCM nudges (often disabled in dev).
 */
class PresenceHeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PresenceHeartbeatScheduler.ACTION) return
        val app = context.applicationContext
        val pending = goAsync()
        Thread({
            try {
                if (!MonitoringPrefs.isPaired(app)) {
                    Log.i(TAG, "HEARTBEAT_ALARM_SKIPPED unpaired")
                    PresenceHeartbeatScheduler.cancel(app)
                    return@Thread
                }
                Log.i(TAG, "HEARTBEAT_ALARM_FIRED")
                val ok = HeartbeatSyncManager.syncNow(app)
                Log.i(TAG, if (ok) "HEARTBEAT_SENT source=alarm" else "HEARTBEAT_FAILED source=alarm")
                try {
                    NativeStompOwner.ensureConnected(app)
                    Log.i(TAG, "SOCKET_CONNECTED source=alarm_reconnect")
                } catch (e: Exception) {
                    Log.w(TAG, "SOCKET_RECONNECT_FAILED reason=${e.message}")
                }
                MonitoringBurstEngine.pollCommandsOnly(app)
            } catch (e: Exception) {
                Log.e(TAG, "HEARTBEAT_ALARM_ERROR reason=${e.message}", e)
            } finally {
                PresenceHeartbeatScheduler.schedule(app)
                pending.finish()
            }
        }, "presence-heartbeat").start()
    }

    companion object {
        private const val TAG = "PresenceHeartbeat"
    }
}
