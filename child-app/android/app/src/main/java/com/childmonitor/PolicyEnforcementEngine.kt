package com.childmonitor

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

object PolicyEnforcementEngine {

    private const val TAG = "PolicyEnforcement"

    private var bundle: JSONObject? = null
    private val usageMsToday = ConcurrentHashMap<String, Long>()
    private val warnedPackages = ConcurrentHashMap.newKeySet<String>()
    private val tempUnlockUntil = ConcurrentHashMap<String, Long>()
    private var usageDay: LocalDate = LocalDate.now()

    /** Reset in-memory usage when the calendar day changes (midnight rollover). */
    private fun ensureUsageDayCurrent() {
        val today = LocalDate.now()
        if (today == usageDay) return
        Log.i(
            TAG,
            "P0_SCREEN_TIME_DAY_ROLLOVER previous=$usageDay today=$today clearedPackages=${usageMsToday.size}",
        )
        usageMsToday.clear()
        warnedPackages.clear()
        usageDay = today
    }

    fun reload(context: Context) {
        bundle = PolicyStore.getBundle(context)
        warnedPackages.clear()
        tempUnlockUntil.clear()
        val grants = bundle?.optJSONArray("timeGrants") ?: return
        val now = System.currentTimeMillis()
        for (i in 0 until grants.length()) {
            val grant = grants.getJSONObject(i)
            val pkg = grant.optString("packageName")
            val expiresAt = grant.optString("expiresAt")
            if (pkg.isBlank() || expiresAt.isBlank()) continue
            try {
                val untilMs = Instant.parse(expiresAt).toEpochMilli()
                if (untilMs > now) {
                    tempUnlockUntil[pkg] = untilMs
                }
            } catch (_: Exception) {
            }
        }
    }

    fun isPackageBlocked(context: Context, packageName: String): Boolean {
        if (isTemporarilyUnlocked(packageName)) return false

        val b = bundle ?: PolicyStore.getBundle(context) ?: return false
        val blocks = b.optJSONArray("appBlocks") ?: JSONArray()
        val now = LocalTime.now()
        val today = DayOfWeek.from(LocalDate.now()).name.substring(0, 3)
        val category = AppCategoryResolver.resolve(context, packageName)

        for (i in 0 until blocks.length()) {
            val rule = blocks.getJSONObject(i)
            if (!rule.optBoolean("enabled", true)) continue
            val pkg = rule.optString("packageName")
            val schedule = rule.optJSONObject("scheduleJson") ?: JSONObject()
            val blockCategory = schedule.optString("appCategory", "")
            if (blockCategory.isNotBlank()) {
                if (blockCategory.equals(category, ignoreCase = true) && isScheduleActive(rule, now, today)) {
                    return true
                }
                continue
            }
            if (pkg == "*" || pkg == "__category__") continue
            if (pkg != packageName) continue
            if (!isScheduleActive(rule, now, today)) continue
            return true
        }

        if (isDeviceScheduleLockActive(b, now, today)) return true
        if (isScreenTimeBlocked(context, b, packageName, category)) return true
        return false
    }

    fun recordUsage(packageName: String, usageMs: Long) {
        ensureUsageDayCurrent()
        usageMsToday.merge(packageName, usageMs) { a, b -> a + b }
    }

    fun shouldWarnScreenTime(context: Context, packageName: String): Boolean {
        ensureUsageDayCurrent()
        val b = bundle ?: PolicyStore.getBundle(context) ?: return false
        val category = AppCategoryResolver.resolve(context, packageName)
        val rules = b.optJSONArray("screenTime") ?: return false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (!matchesScreenTimeTarget(rule, packageName, category)) continue
            val limitMin = effectiveDailyLimitMinutes(rule)
            val warnPct = rule.optInt("warnAtPercent", 80)
            val used = usageMsToday[packageName] ?: 0L
            val threshold = limitMin * 60_000L * warnPct / 100
            if (used >= threshold && !warnedPackages.contains(packageName)) {
                warnedPackages.add(packageName)
                return true
            }
        }
        return false
    }

    fun showBlockScreen(context: Context, packageName: String, reason: String = "blocked") {
        val intent = Intent(context, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("packageName", packageName)
            putExtra("blockReason", reason)
        }
        context.startActivity(intent)
    }

    fun reportViolation(context: Context, type: String, packageName: String?, policyId: String?) {
        Thread {
            PolicyViolationUploader.upload(context, type, packageName, policyId, null)
        }.start()
    }

    private fun isTemporarilyUnlocked(packageName: String): Boolean {
        val until = tempUnlockUntil[packageName] ?: return false
        if (System.currentTimeMillis() >= until) {
            tempUnlockUntil.remove(packageName)
            return false
        }
        return true
    }

    private fun isScreenTimeBlocked(
        context: Context,
        b: JSONObject,
        packageName: String,
        category: String,
    ): Boolean {
        ensureUsageDayCurrent()
        val rules = b.optJSONArray("screenTime") ?: return false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (!matchesScreenTimeTarget(rule, packageName, category)) continue
            val limitMin = effectiveDailyLimitMinutes(rule)
            if (limitMin <= 0) continue
            val used = usageMsToday[packageName] ?: 0L
            if (used >= limitMin * 60_000L) {
                val action = rule.optString("actionOnLimit", "BLOCK_APP")
                return action == "BLOCK_APP" || action == "LOCK_DEVICE"
            }
        }
        return false
    }

    private fun matchesScreenTimeTarget(rule: JSONObject, packageName: String, category: String): Boolean {
        val pkg = rule.optString("packageName", "")
        val cat = rule.optString("appCategory", "")
        if (cat.isNotBlank()) return cat.equals(category, ignoreCase = true)
        if (pkg.isNotBlank()) return pkg == packageName
        return true
    }

    private fun effectiveDailyLimitMinutes(rule: JSONObject): Int {
        val weekly = rule.optJSONObject("weeklyScheduleJson")
        if (weekly != null && weekly.length() > 0) {
            val today = DayOfWeek.from(LocalDate.now()).name.substring(0, 3).uppercase()
            val dayRule = weekly.optJSONObject(today)
            if (dayRule != null) {
                if (!dayRule.optBoolean("enabled", true)) return Int.MAX_VALUE
                return dayRule.optInt("limitMinutes", rule.optInt("dailyLimitMinutes", 60))
            }
        }
        return rule.optInt("dailyLimitMinutes", 60)
    }

    fun isScreenTimeOnlyBlock(context: Context, packageName: String): Boolean {
        if (isTemporarilyUnlocked(packageName)) return false
        val b = bundle ?: PolicyStore.getBundle(context) ?: return false
        val category = AppCategoryResolver.resolve(context, packageName)
        return isScreenTimeBlocked(context, b, packageName, category)
    }

    private fun isDeviceScheduleLockActive(b: JSONObject, now: LocalTime, today: String): Boolean {
        val schedules = b.optJSONArray("deviceSchedules") ?: return false
        for (i in 0 until schedules.length()) {
            val s = schedules.getJSONObject(i)
            if (!s.optBoolean("enabled", true)) continue
            if (!s.optBoolean("lockDevice", false)) continue
            val days = s.optJSONArray("daysOfWeek") ?: JSONArray()
            var dayOk = days.length() == 0
            for (d in 0 until days.length()) {
                if (days.getString(d).equals(today, true)) dayOk = true
            }
            if (!dayOk) continue
            val start = LocalTime.parse(s.getString("startTime"))
            val end = LocalTime.parse(s.getString("endTime"))
            if (isWithin(now, start, end)) return true
        }
        return false
    }

    private fun isScheduleActive(rule: JSONObject, now: LocalTime, today: String): Boolean {
        val type = rule.optString("scheduleType", "ALWAYS")
        if (type == "ALWAYS") return true
        val schedule = rule.optJSONObject("scheduleJson") ?: JSONObject()
        if (type == "RECURRING") {
            val days = schedule.optJSONArray("daysOfWeek") ?: JSONArray()
            var dayOk = days.length() == 0
            for (d in 0 until days.length()) {
                if (days.getString(d).equals(today, true)) dayOk = true
            }
            if (!dayOk) return false
        }
        val start = schedule.optString("startTime", null) ?: return true
        val end = schedule.optString("endTime", null) ?: return true
        return isWithin(now, LocalTime.parse(start), LocalTime.parse(end))
    }

    private fun isWithin(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start <= end) {
            !now.isBefore(start) && !now.isAfter(end)
        } else {
            !now.isBefore(start) || !now.isAfter(end)
        }
    }

    @VisibleForTesting
    internal fun resetUsageCountersForTests() {
        usageMsToday.clear()
        warnedPackages.clear()
        usageDay = LocalDate.now()
    }

    @VisibleForTesting
    internal fun setUsageDayAndMsForTests(day: LocalDate, packageName: String, usageMs: Long) {
        usageDay = day
        usageMsToday[packageName] = usageMs
    }

    @VisibleForTesting
    internal fun usageMsTodayForTests(packageName: String): Long = usageMsToday[packageName] ?: 0L
}
