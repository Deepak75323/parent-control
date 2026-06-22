package com.childmonitor

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCaptureService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val body = extras.getCharSequence("android.text")?.toString()
        NotificationStore.add(
            context = applicationContext,
            packageName = sbn.packageName,
            title = title,
            body = body,
            postedAtMs = sbn.postTime,
        )
    }
}
