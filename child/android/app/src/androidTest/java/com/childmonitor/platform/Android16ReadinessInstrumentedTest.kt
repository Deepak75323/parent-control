package com.childmonitor.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.childmonitor.MonitoringPrefs
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Android16ReadinessInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MonitoringPrefs.setPaired(context, true)
    }

    @Test
    fun evaluate_returnsScoreAndChecks() {
        val report = Android16ReadinessEvaluator.evaluate(context)
        assertTrue(report.score in 0..100)
        assertTrue(report.checks.isNotEmpty())
    }

    @Test
    fun platformCapabilities_includeSdkInt() {
        val caps = AndroidPlatform.capabilities(context)
        assertTrue(caps.containsKey("sdkInt"))
        assertTrue(caps.containsKey("targetAndroid16"))
    }
}
