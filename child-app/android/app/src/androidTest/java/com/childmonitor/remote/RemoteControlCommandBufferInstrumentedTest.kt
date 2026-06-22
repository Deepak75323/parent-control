package com.childmonitor.remote

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.childmonitor.ForegroundAppTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteControlCommandBufferInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        RemoteControlCommandBuffer.resetForTests(context)
    }

    @Test
    fun isStaleScreen_returnsFalseForGlobalActions() {
        val entry = RemoteControlCommandQueue.Entry(
            commandId = "1",
            commandType = "GLOBAL_BACK",
            payload = null,
            enqueuedAtMs = System.currentTimeMillis(),
            foregroundPackage = "com.a",
        )
        ForegroundAppTracker.update("com.b")
        assertFalse(RemoteControlCommandBuffer.isStaleScreen(entry))
    }

    @Test
    fun isStaleScreen_detectsForegroundChangeForTap() {
        val entry = RemoteControlCommandQueue.Entry(
            commandId = "1",
            commandType = "TAP",
            payload = null,
            enqueuedAtMs = System.currentTimeMillis(),
            foregroundPackage = "com.example.chrome",
        )
        ForegroundAppTracker.update("com.other.app")
        assertTrue(RemoteControlCommandBuffer.isStaleScreen(entry))
    }

    @Test
    fun isStaleScreen_allowsTapWhenForegroundUnchanged() {
        val pkg = "com.example.game"
        val entry = RemoteControlCommandQueue.Entry(
            commandId = "1",
            commandType = "TAP",
            payload = null,
            enqueuedAtMs = System.currentTimeMillis(),
            foregroundPackage = pkg,
        )
        ForegroundAppTracker.update(pkg)
        assertFalse(RemoteControlCommandBuffer.isStaleScreen(entry))
    }
}
