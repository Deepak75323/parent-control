package com.childmonitor.monitoring

import android.content.Context
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.platform.AndroidPlatform
import com.childmonitor.sync.HeartbeatSyncManager

/**
 * P1-08 orchestration: choose monitoring path under dataSync quota + standby limits.
 *
 * Does not attempt to bypass Android 15/16 quotas — only degrades and recovers.
 */
object MonitoringDegradationController {

    private const val TAG = "MonitoringDegrade"
    private const val PREFS = "monitoring_degradation"
    private const val KEY_MODE = "mode"
    private const val KEY_REASON = "reason"
    private const val KEY_SINCE_MS = "since_ms"
    private const val KEY_PARENT_NOTIFIED = "parent_notified"
    private const val KEY_LAST_EVAL_MS = "last_eval_ms"

    data class Snapshot(
        val mode: MonitoringMode,
        val degraded: Boolean,
        val reason: String,
        val standbyBucket: String,
        val quota: Map<String, Any>,
        val sinceMs: Long,
        val parentNotified: Boolean,
    )

    @JvmStatic
    fun currentMode(context: Context): MonitoringMode {
        if (!AndroidPlatform.useBurstFgs()) return MonitoringMode.NORMAL
        evaluate(context)
        return readMode(context)
    }

    @JvmStatic
    fun isDegraded(context: Context): Boolean = currentMode(context).isDegraded()

    /**
     * When true, skip starting MonitoringForegroundService dataSync burst.
     * FCM/USER still run [MonitoringBurstEngine] under a wake lock without FGS.
     *
     * @param forceUrgent when true (FCM/USER), allow bursts in CONSERVATIVE / STANDBY_DEGRADED
     * unless the daily quota is exhausted.
     */
    @JvmStatic
    fun shouldSkipBurstFgs(context: Context, forceUrgent: Boolean = false): Boolean {
        if (!AndroidPlatform.useBurstFgs()) return false
        if (DataSyncFgsQuotaTracker.isExhausted(context)) return true
        val mode = currentMode(context)
        return when (mode) {
            MonitoringMode.WORKMANAGER_ONLY -> true
            MonitoringMode.STANDBY_DEGRADED -> !forceUrgent
            MonitoringMode.CONSERVATIVE -> !forceUrgent
            MonitoringMode.NORMAL -> false
        }
    }

    @JvmStatic
    fun onFgsTimeout(context: Context) {
        DataSyncFgsQuotaTracker.onTimeout(context)
        enterMode(context, MonitoringMode.WORKMANAGER_ONLY, "fgs_timeout")
        // WorkManager one-shot / boot chain remain the caller's responsibility
        // (MonitoringForegroundService.onTimeout) to avoid duplicate schedules.
        MonitoringWorkScheduler.ensurePeriodicMonitoring(context)
        notifyParentIfNeeded(context)
    }

    @JvmStatic
    fun onFgsStartFailure(context: Context) {
        // Start failure may be temporary (BAL / background) — do not burn the daily quota flag.
        enterMode(context, MonitoringMode.CONSERVATIVE, "fgs_start_failure")
        MonitoringWorkScheduler.ensurePeriodicMonitoring(context)
        MonitoringWorkScheduler.scheduleOneShot(context, 0L, "p1_08_fgs_start_failure")
        notifyParentIfNeeded(context)
    }

    @JvmStatic
    fun onBurstCompletedSuccessfully(context: Context) {
        // Successful burst does not clear OS quota; re-evaluate soft near-quota / standby.
        evaluate(context)
    }

    @JvmStatic
    fun evaluate(context: Context): MonitoringMode {
        if (!AndroidPlatform.useBurstFgs()) {
            enterMode(context, MonitoringMode.NORMAL, "pre_android15")
            return MonitoringMode.NORMAL
        }
        val app = context.applicationContext
        val bucket = StandbyBucketReader.bucketName(app)
        val next = when {
            DataSyncFgsQuotaTracker.isExhausted(app) ->
                MonitoringMode.WORKMANAGER_ONLY to "quota_exhausted"
            StandbyBucketReader.isSparseScheduling(bucket) ->
                MonitoringMode.STANDBY_DEGRADED to "standby_$bucket"
            DataSyncFgsQuotaTracker.isNearQuota(app) ->
                MonitoringMode.CONSERVATIVE to "near_quota"
            else -> MonitoringMode.NORMAL to "healthy"
        }
        enterMode(app, next.first, next.second)
        prefs(app).edit().putLong(KEY_LAST_EVAL_MS, System.currentTimeMillis()).apply()
        return next.first
    }

    @JvmStatic
    fun snapshot(context: Context): Snapshot {
        val mode = currentMode(context)
        val p = prefs(context)
        return Snapshot(
            mode = mode,
            degraded = mode.isDegraded(),
            reason = p.getString(KEY_REASON, "") ?: "",
            standbyBucket = StandbyBucketReader.bucketName(context),
            quota = DataSyncFgsQuotaTracker.snapshot(context),
            sinceMs = p.getLong(KEY_SINCE_MS, 0L),
            parentNotified = p.getBoolean(KEY_PARENT_NOTIFIED, false),
        )
    }

    @JvmStatic
    fun heartbeatFields(context: Context): Map<String, Any> {
        val snap = snapshot(context)
        val fields = linkedMapOf<String, Any>(
            "monitoringMode" to snap.mode.name,
            "monitoringDegraded" to snap.degraded,
            "monitoringDegradedReason" to snap.reason,
            "standbyBucket" to snap.standbyBucket,
        )
        fields.putAll(snap.quota)
        return fields
    }

    @JvmStatic
    fun resetForTests(context: Context) {
        prefs(context).edit().clear().apply()
        DataSyncFgsQuotaTracker.clearExhaustionForTests(context)
    }

    private fun enterMode(context: Context, mode: MonitoringMode, reason: String) {
        val p = prefs(context)
        val previous = readMode(context)
        if (previous == mode && (p.getString(KEY_REASON, "") == reason)) return
        val now = System.currentTimeMillis()
        val wasDegraded = previous.isDegraded()
        val nowDegraded = mode.isDegraded()
        p.edit()
            .putString(KEY_MODE, mode.name)
            .putString(KEY_REASON, reason)
            .putLong(KEY_SINCE_MS, if (nowDegraded && !wasDegraded) now else p.getLong(KEY_SINCE_MS, now))
            .putBoolean(KEY_PARENT_NOTIFIED, if (!nowDegraded) false else p.getBoolean(KEY_PARENT_NOTIFIED, false))
            .apply()
        Log.i(TAG, "mode $previous → $mode reason=$reason")
        PcpFlowLogger.log(
            PcpFlowLogger.Category.SERVICE_STATE,
            "P1-08 monitoring mode",
            "from=$previous to=$mode reason=$reason",
        )
        if (nowDegraded && !wasDegraded) {
            notifyParentIfNeeded(context)
        }
    }

    private fun notifyParentIfNeeded(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_PARENT_NOTIFIED, false)) return
        if (!readMode(context).isDegraded()) return
        p.edit().putBoolean(KEY_PARENT_NOTIFIED, true).apply()
        val app = context.applicationContext
        Thread {
            val ok = HeartbeatSyncManager.syncMonitoringDegraded(app)
            Log.i(TAG, "parent notified of monitoring degradation ok=$ok")
        }.start()
    }

    private fun readMode(context: Context): MonitoringMode {
        val raw = prefs(context).getString(KEY_MODE, MonitoringMode.NORMAL.name)
        return try {
            MonitoringMode.valueOf(raw ?: MonitoringMode.NORMAL.name)
        } catch (_: Exception) {
            MonitoringMode.NORMAL
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
