package com.childmonitor.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteControlCommandStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RemoteControlCommandStore.clear(context)
    }

    @After
    fun tearDown() {
        RemoteControlCommandStore.clear(context)
    }

    @Test
    fun enqueue_persistsAcrossReload() {
        val entry = stored("cmd-1", "TAP")
        assertTrue(RemoteControlCommandStore.enqueue(context, entry))
        assertEquals(1, RemoteControlCommandStore.queuedSize(context))
        val drained = RemoteControlCommandStore.drainQueued(context)
        assertEquals(1, drained.size)
        assertEquals("cmd-1", drained[0].commandId)
        assertEquals(0, RemoteControlCommandStore.queuedSize(context))
    }

    @Test
    fun enqueue_deduplicatesByCommandId() {
        val entry = stored("cmd-1", "TAP")
        assertTrue(RemoteControlCommandStore.enqueue(context, entry))
        assertFalse(RemoteControlCommandStore.enqueue(context, entry))
        assertEquals(1, RemoteControlCommandStore.queuedSize(context))
    }

    @Test
    fun deadLetter_marksEntry() {
        val entry = stored("cmd-1", "TAP")
        RemoteControlCommandStore.enqueue(context, entry)
        RemoteControlCommandStore.deadLetter(context, "cmd-1", "test_reason")
        assertEquals(0, RemoteControlCommandStore.queuedSize(context))
        assertEquals(1, RemoteControlCommandStore.deadLetterEntries(context).size)
    }

    private fun stored(id: String, type: String) = RemoteControlCommandStore.StoredEntry(
        commandId = id,
        commandType = type,
        payload = null,
        enqueuedAtMs = System.currentTimeMillis(),
        foregroundPackage = "com.example.app",
    )
}
