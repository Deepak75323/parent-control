package com.childmonitor.monitoring

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * Reads this app's [UsageStatsManager] standby bucket (no special permission for own UID).
 *
 * Standby buckets throttle WorkManager/JobScheduler — RARE/RESTRICTED can mean ≤1 job/day,
 * which is why P1-08 must not treat WorkManager as a full FGS replacement.
 */
object StandbyBucketReader {

    const val UNKNOWN = "UNKNOWN"
    const val ACTIVE = "ACTIVE"
    const val WORKING_SET = "WORKING_SET"
    const val FREQUENT = "FREQUENT"
    const val RARE = "RARE"
    const val RESTRICTED = "RESTRICTED"

    @JvmStatic
    fun bucketName(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return UNKNOWN
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return UNKNOWN
            nameFor(usm.appStandbyBucket)
        } catch (_: Exception) {
            UNKNOWN
        }
    }

    @JvmStatic
    fun isSparseScheduling(bucketName: String): Boolean =
        bucketName == RARE || bucketName == RESTRICTED

    @JvmStatic
    fun nameFor(bucket: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return UNKNOWN
        return when (bucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> ACTIVE
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> WORKING_SET
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> FREQUENT
            UsageStatsManager.STANDBY_BUCKET_RARE -> RARE
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) RESTRICTED else UNKNOWN
            else -> UNKNOWN
        }
    }
}
