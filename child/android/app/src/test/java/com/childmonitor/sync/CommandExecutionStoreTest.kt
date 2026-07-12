package com.childmonitor.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CommandExecutionStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("command_execution_fingerprints", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun markExecuted_persistsAndPreventsDuplicateLookup() {
        assertFalse(CommandExecutionStore.hasExecuted(context, "cmd-1"))
        CommandExecutionStore.markExecuted(context, "cmd-1", true, null)
        assertTrue(CommandExecutionStore.hasExecuted(context, "cmd-1"))
        val record = CommandExecutionStore.get(context, "cmd-1")
        assertNotNull(record)
        assertEquals(true, record!!.success)
        assertFalse(record.serverRecorded)
        assertFalse(record.acked)
    }

    @Test
    fun pendingFinalizations_returnsIncompleteRecords() {
        CommandExecutionStore.markExecuted(context, "cmd-1", true, null)
        val pending = CommandExecutionStore.pendingFinalizations(context)
        assertEquals(1, pending.size)
        assertEquals("cmd-1", pending[0].commandId)
    }

    @Test
    fun remove_clearsFingerprint() {
        CommandExecutionStore.markExecuted(context, "cmd-1", false, "failed")
        CommandExecutionStore.remove(context, "cmd-1")
        assertNull(CommandExecutionStore.get(context, "cmd-1"))
    }

    @Test
    fun markAcked_andServerRecorded_updatesFlags() {
        CommandExecutionStore.markExecuted(context, "cmd-1", true, null)
        CommandExecutionStore.markServerRecorded(context, "cmd-1")
        CommandExecutionStore.markAcked(context, "cmd-1")
        val record = CommandExecutionStore.get(context, "cmd-1")
        assertNotNull(record)
        assertTrue(record!!.serverRecorded)
        assertTrue(record.acked)
    }
}
