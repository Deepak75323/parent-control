package com.childmonitor.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents the contract used to stop the STOMP reconnect storm:
 * same normalized API base must not trigger a reconnect.
 */
class ApiBaseReconnectPolicyTest {

    @Test
    fun normalize_trimsTrailingSlash() {
        assertEquals("http://80.225.196.181", "http://80.225.196.181/".trimEnd('/'))
        assertEquals("http://80.225.196.181", "http://80.225.196.181".trimEnd('/'))
    }

    @Test
    fun shouldReconnect_onlyWhenBaseChanges() {
        fun shouldReconnect(previous: String?, next: String, connected: Boolean): Boolean {
            val normalized = next.trimEnd('/')
            val prev = previous?.trimEnd('/')
            if (prev == normalized && connected) return false
            return prev != normalized
        }

        assertTrue(shouldReconnect(null, "http://80.225.196.181", false))
        assertTrue(shouldReconnect("http://old", "http://80.225.196.181", true))
        assertFalse(shouldReconnect("http://80.225.196.181", "http://80.225.196.181/", true))
        assertFalse(shouldReconnect("http://80.225.196.181/", "http://80.225.196.181", true))
    }
}
