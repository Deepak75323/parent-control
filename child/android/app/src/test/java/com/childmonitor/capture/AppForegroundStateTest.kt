package com.childmonitor.capture

import android.app.ActivityManager
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppForegroundStateTest {

    @Test
    fun visibleActivityMakesWhileInUseEligible() {
        AppForegroundState.onActivityVisible()
        try {
            assertTrue(AppForegroundState.hasVisibleActivity())
            assertTrue(AppForegroundState.canPromoteWhileInUseFgs(RuntimeEnvironment.getApplication()))
        } finally {
            AppForegroundState.onActivityHidden()
        }
    }

    @Test
    fun noVisibleActivityReportsNotVisible() {
        // Drain any leftover visibility from other tests in the same JVM.
        repeat(5) { AppForegroundState.onActivityHidden() }
        assertFalse(AppForegroundState.hasVisibleActivity())
    }
}
