package com.childmonitor

import android.content.Context
import android.util.Log
import com.childmonitor.deviceowner.DeviceOwnerManager
import com.childmonitor.fcm.FcmTokenRegistrar
import com.childmonitor.sync.DeviceCommandProcessor
import com.childmonitor.sync.DeviceHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import com.childmonitor.constants.ApiPaths

object NativeSyncUploader {
    private const val TAG = "NativeSyncUploader"

    fun syncAll(context: Context): Boolean {
        if (!MonitoringPrefs.isPaired(context)) return false

        DeviceCommandProcessor.pollAndExecute(context)

        var ok = true
        ok = syncNotifications(context) && ok
        ok = syncLocation(context) && ok
        ok = syncUsageStats(context) && ok
        ok = syncInstalledApps(context, force = false) && ok
        ok = syncHeartbeat(context) && ok
        return ok
    }

    private fun syncNotifications(context: Context): Boolean {
        val pending = NotificationStore.getPending(context)
        if (pending.isEmpty()) return true

        val arr = JSONArray()
        pending.forEach {
            arr.put(
                JSONObject()
                    .put("packageName", it.packageName)
                    .put("title", it.title)
                    .put("body", it.body)
                    .put("postedAt", Instant.ofEpochMilli(it.postedAtMs).toString()),
            )
        }

        val body = JSONObject().put("notifications", arr).toString()
        val success = DeviceHttpClient.postJson(context, ApiPaths.DEVICE_NOTIFICATIONS, body)
        if (success) {
            NotificationStore.markSynced(context, pending.map { it.localId })
        }
        return success
    }

    private fun syncLocation(context: Context): Boolean {
        if (!LocationReader.hasPermission(context)) return true
        val loc = LocationReader.getLastKnown(context) ?: return true
        GeofenceMonitor.checkLocation(context, loc.latitude, loc.longitude)
        val body = JSONObject()
            .put("latitude", loc.latitude)
            .put("longitude", loc.longitude)
            .put("accuracyM", loc.accuracyM?.toDouble())
            .put("recordedAt", Instant.now().toString())
            .toString()
        return DeviceHttpClient.postJson(context, ApiPaths.DEVICE_LOCATION, body)
    }

    private fun syncUsageStats(context: Context): Boolean {
        if (!UsageStatsReader.hasPermission(context)) return true
        val since = MonitoringPrefs.lastUsageSyncMs(context)
        val deltaEntries = UsageStatsReader.readSince(context, since)

        // Always update the enforcement engine with today's full usage so that
        // limits survive process restarts (usageMsToday is in-memory only; if the
        // service is killed mid-day, accumulated delta counters are lost, meaning
        // limits won't fire for the rest of the day).
        val todayEntries = UsageStatsReader.readSince(context, UsageStatsReader.startOfTodayMs())
        todayEntries.forEach { PolicyEnforcementEngine.setUsageToday(it.packageName, it.usageMs) }

        if (deltaEntries.isEmpty()) return true

        val arr = JSONArray()
        deltaEntries.forEach {
            arr.put(
                JSONObject()
                    .put("packageName", it.packageName)
                    .put("usageMs", it.usageMs)
                    .put("recordedAt", it.recordedAt.toString()),
            )
        }

        val body = JSONObject().put("entries", arr).toString()
        val success = DeviceHttpClient.postJson(context, ApiPaths.DEVICE_USAGE_STATS, body)
        if (success) {
            MonitoringPrefs.setLastUsageSyncMs(context, System.currentTimeMillis())
        }
        return success
    }

    /** Push installed-app inventory to backend (for parent app picker). */
    fun forceSyncInstalledApps(context: Context): Boolean {
        return syncInstalledApps(context, force = true)
    }

    private fun syncInstalledApps(context: Context, force: Boolean = false): Boolean {
        val lastSync = MonitoringPrefs.lastInstalledAppsSyncMs(context)
        val now = System.currentTimeMillis()
        if (!force && lastSync > 0 && now - lastSync < 15 * 60 * 1000L) {
            return true
        }

        val apps = InstalledAppsReader.listLaunchableApps(context)
        if (apps.isEmpty()) return true

        val arr = JSONArray()
        apps.forEach {
            arr.put(
                JSONObject()
                    .put("packageName", it.packageName)
                    .put("appLabel", it.appLabel)
                    .put("appCategory", it.appCategory),
            )
        }

        val body = JSONObject().put("apps", arr).toString()
        val success = DeviceHttpClient.postJson(context, ApiPaths.DEVICE_INSTALLED_APPS, body)
        if (success) {
            MonitoringPrefs.setLastInstalledAppsSyncMs(context, now)
        }
        return success
    }

    private fun syncHeartbeat(context: Context): Boolean =
        com.childmonitor.sync.HeartbeatSyncManager.syncNow(context)

    /** Immediate heartbeat when projection consent is lost — updates parent dashboard. */
    fun forceProjectionLostHeartbeat(context: Context): Boolean =
        com.childmonitor.sync.HeartbeatSyncManager.syncProjectionLost(context, "legacy_api")

    /** Immediate heartbeat after projection is re-granted — restores capability flags. */
    fun syncProjectionCapabilityHeartbeat(context: Context): Boolean =
        com.childmonitor.sync.HeartbeatSyncManager.syncProjectionRestored(context)

    /** Immediate heartbeat when accessibility service connects or disconnects. */
    fun syncAccessibilityServiceState(context: Context, connected: Boolean): Boolean =
        com.childmonitor.sync.HeartbeatSyncManager.syncAccessibilityServiceState(context, connected)
}
