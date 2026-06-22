package com.childmonitor.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingClaimStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PendingClaimStore.clear(context)
    }

    @After
    fun tearDown() {
        PendingClaimStore.clear(context)
    }

    @Test
    fun saveAndReload_survivesProcessDeath() {
        PendingClaimStore.save(context, "cmd-1", "GLOBAL_BACK", null)
        val claims = PendingClaimStore.all(context)
        assertEquals(1, claims.size)
        assertEquals("cmd-1", claims[0].commandId)
    }

    @Test
    fun remove_clearsClaim() {
        PendingClaimStore.save(context, "cmd-1", "TAP", null)
        PendingClaimStore.remove(context, "cmd-1")
        assertTrue(PendingClaimStore.all(context).isEmpty())
    }
}
