package com.childmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolicyEnforcementEngineTest {

    @Before
    fun setUp() {
        PolicyEnforcementEngine.resetUsageCountersForTests()
    }

    @Test
    fun blockAllApps_blocksNormalApp_allowsDialer() {
        val context = RuntimeEnvironment.getApplication()
        val bundle =
            JSONObject().put(
                "appBlocks",
                JSONArray().put(
                    JSONObject()
                        .put("enabled", true)
                        .put("packageName", "*")
                        .put("scheduleType", "ALWAYS")
                        .put("scheduleJson", JSONObject().put("blockAllApps", true)),
                ),
            )
        PolicyEnforcementEngine.setBundleForTests(bundle)

        assertTrue(PolicyEnforcementEngine.isPackageBlocked(context, "com.instagram.android"))
        assertFalse(PolicyEnforcementEngine.isPackageBlocked(context, "com.google.android.dialer"))
        assertFalse(PolicyEnforcementEngine.isExemptFromLock(context, "com.instagram.android"))
        assertTrue(PolicyEnforcementEngine.isExemptFromLock(context, "com.android.systemui"))
    }

    @Test
    fun screenTimeLimit_blocksWhenUsageExceedsDailyLimit() {
        val context = RuntimeEnvironment.getApplication()
        val bundle =
            JSONObject().put(
                "screenTime",
                JSONArray().put(
                    JSONObject()
                        .put("enabled", true)
                        .put("packageName", "com.google.android.youtube")
                        .put("dailyLimitMinutes", 15)
                        .put("actionOnLimit", "BLOCK_APP")
                        .put("weeklyScheduleJson", JSONObject()),
                ),
            )
        PolicyEnforcementEngine.setBundleForTests(bundle)
        PolicyEnforcementEngine.setUsageToday("com.google.android.youtube", 16 * 60_000L)

        assertTrue(PolicyEnforcementEngine.isPackageBlocked(context, "com.google.android.youtube"))
        assertTrue(PolicyEnforcementEngine.isScreenTimeOnlyBlock(context, "com.google.android.youtube"))
        assertFalse(PolicyEnforcementEngine.isPackageBlocked(context, "com.android.chrome"))
    }

    /**
     * Backend policy bundles often serialize missing category as JSON null
     * (`"appCategory":null`). Android org.json maps that to the string "null" via
     * optString — matching must treat it as absent and fall through to packageName.
     */
    @Test
    fun screenTimeLimit_matchesPackageWhenAppCategoryJsonNull() {
        val context = RuntimeEnvironment.getApplication()
        val bundle =
            JSONObject(
                """
                {
                  "screenTime": [{
                    "enabled": true,
                    "packageName": "com.google.android.youtube",
                    "appCategory": null,
                    "dailyLimitMinutes": 15,
                    "actionOnLimit": "BLOCK_APP",
                    "weeklyScheduleJson": {}
                  }]
                }
                """.trimIndent(),
            )
        PolicyEnforcementEngine.setBundleForTests(bundle)
        PolicyEnforcementEngine.setUsageToday("com.google.android.youtube", 18 * 60_000L)

        assertTrue(PolicyEnforcementEngine.isPackageBlocked(context, "com.google.android.youtube"))
        assertTrue(PolicyEnforcementEngine.isScreenTimeOnlyBlock(context, "com.google.android.youtube"))
    }

    @Test
    fun recordUsage_resetsCountersOnNewCalendarDay() {
        val yesterday = LocalDate.now().minusDays(1)
        PolicyEnforcementEngine.setUsageDayAndMsForTests(yesterday, "com.tiktok", 45 * 60_000L)

        PolicyEnforcementEngine.recordUsage("com.tiktok", 10 * 60_000L)

        assertEquals(10 * 60_000L, PolicyEnforcementEngine.usageMsTodayForTests("com.tiktok"))
    }

    @Test
    fun recordUsage_accumulatesWithinSameCalendarDay() {
        PolicyEnforcementEngine.recordUsage("com.tiktok", 20 * 60_000L)
        PolicyEnforcementEngine.recordUsage("com.tiktok", 15 * 60_000L)

        assertEquals(35 * 60_000L, PolicyEnforcementEngine.usageMsTodayForTests("com.tiktok"))
    }
}
