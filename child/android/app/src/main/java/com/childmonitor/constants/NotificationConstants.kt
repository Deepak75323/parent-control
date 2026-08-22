package com.childmonitor.constants

/**
 * Notification channel IDs used by the child app foreground services.
 */
object NotificationConstants {
    /** Legacy id — deleted at runtime; live silent FGS uses [SilentNotificationHelper.CHANNEL_ID]. */
    const val CHANNEL_MONITORING_SILENT = "monitoring_silent"
    const val CHANNEL_SETUP_ACCESS = "setup_access"
    const val CHANNEL_STREAM_REQUEST = "stream_request_v3"
}
