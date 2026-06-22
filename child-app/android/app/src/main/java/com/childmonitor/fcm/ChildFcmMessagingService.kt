package com.childmonitor.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * High-priority FCM data messages wake [com.childmonitor.MonitoringForegroundService] when STOMP is suspended.
 * Uses [FcmWakeHandler] for Doze-safe partial wake lock + FGS restart.
 */
class ChildFcmMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM onNewToken")
        FcmWakeHandler.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        FcmWakeHandler.handleMessage(applicationContext, message.data)
    }

    companion object {
        private const val TAG = "ChildFcmMessaging"
    }
}
