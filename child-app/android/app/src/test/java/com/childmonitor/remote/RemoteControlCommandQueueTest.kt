package com.childmonitor.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteControlCommandQueueTest {

    private var now = 0L
    private lateinit var queue: RemoteControlCommandQueue
    private val expired = mutableListOf<RemoteControlCommandQueue.Entry>()

    @Before
    fun setUp() {
        now = 1_000_000L
        expired.clear()
        queue = RemoteControlCommandQueue(
            maxSize = 2,
            expiryMs = 10_000L,
            nowMs = { now },
        )
    }

    @Test
    fun enqueue_deduplicatesByCommandId() {
        val entry = entry("cmd-1", "TAP")
        assertTrue(queue.enqueue(entry))
        assertFalse(queue.enqueue(entry))
        assertEquals(1, queue.size())
    }

    @Test
    fun enqueue_evictsOldestWhenFull() {
        queue.enqueue(entry("cmd-1", "TAP"))
        queue.enqueue(entry("cmd-2", "TAP"))
        queue.enqueue(entry("cmd-3", "TAP"))
        assertEquals(2, queue.size())
        val drained = queue.drain()
        assertEquals(listOf("cmd-2", "cmd-3"), drained.map { it.commandId })
    }

    @Test
    fun expireStale_removesOldEntries() {
        queue.enqueue(entry("cmd-old", "TAP"))
        now += 10_001L
        queue.enqueue(entry("cmd-new", "TAP"))
        queue.expireStale { expired.add(it) }
        assertEquals(listOf("cmd-old"), expired.map { it.commandId })
        assertEquals(1, queue.size())
    }

    @Test
    fun drain_clearsQueue() {
        queue.enqueue(entry("cmd-1", "TAP"))
        val drained = queue.drain()
        assertEquals(1, drained.size)
        assertEquals(0, queue.size())
    }

    private fun entry(id: String, type: String): RemoteControlCommandQueue.Entry {
        return RemoteControlCommandQueue.Entry(
            commandId = id,
            commandType = type,
            payload = null,
            enqueuedAtMs = now,
            foregroundPackage = "com.example.app",
        )
    }
}
