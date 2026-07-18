package com.childmonitor

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCaptureService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val rawTitle = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence("android.text")?.toString()
        val appLabel = resolveAppLabel(sbn.packageName)
        // Prefer the notification title; if missing, use the app's display name — never leave
        // parents staring at a raw package id like com.miui.screenshot.
        val title = when {
            rawTitle.isNotBlank() && !looksLikePackageId(rawTitle) -> rawTitle
            !appLabel.isNullOrBlank() -> appLabel
            else -> null
        }
        NotificationStore.add(
            context = applicationContext,
            packageName = sbn.packageName,
            title = title,
            body = body,
            postedAtMs = sbn.postTime,
        )
    }

    private fun resolveAppLabel(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun looksLikePackageId(value: String): Boolean =
        value.contains('.') && value.matches(Regex("^[a-zA-Z][\\w]*(\\.[a-zA-Z][\\w]*)+$"))
}
