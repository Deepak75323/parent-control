package com.childmonitor.capture

import android.os.Build

/**
 * P1-07 — Android 14+ MediaProjection / foreground-service ordering.
 *
 * Required sequence (API 34–36):
 * 1. User grant via [android.media.projection.MediaProjectionManager.createScreenCaptureIntent]
 *    (fresh dialog or previously stored consent token)
 * 2. Optional: bootstrap [android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC]
 *    to satisfy the ~5s startForeground deadline
 * 3. [android.app.Service.startForeground] with
 *    [android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION]
 * 4. [android.media.projection.MediaProjectionManager.getMediaProjection]
 *
 * Calling step 4 before step 3 throws [SecurityException] on Android 14+.
 */
object MediaProjectionFgsOrdering {

    /** API levels that enforce mediaProjection-typed FGS before getMediaProjection. */
    @JvmStatic
    fun requiresMediaProjectionTypeBeforeObtain(apiLevel: Int): Boolean =
        apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * @return true when it is legal to call getMediaProjection for [apiLevel]
     * given whether the screen FGS currently holds the mediaProjection type.
     */
    @JvmStatic
    fun canCallGetMediaProjection(apiLevel: Int, mediaProjectionTypeActive: Boolean): Boolean {
        if (!requiresMediaProjectionTypeBeforeObtain(apiLevel)) return true
        return mediaProjectionTypeActive
    }

    /** Human-readable ordered steps for logs / validation. */
    @JvmStatic
    fun requiredSequence(): List<String> = listOf(
        "user_consent",
        "startForeground_mediaProjection",
        "getMediaProjection",
    )
}
