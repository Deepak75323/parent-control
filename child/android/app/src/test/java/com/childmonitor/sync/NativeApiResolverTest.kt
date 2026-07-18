package com.childmonitor.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeApiResolverTest {

    @Test
    fun isLocalDevHost_detectsLoopbackAndEmulator() {
        assertTrue(NativeApiResolver.isLocalDevHost("http://127.0.0.1:8080"))
        assertTrue(NativeApiResolver.isLocalDevHost("http://10.0.2.2:8080"))
        assertTrue(NativeApiResolver.isLocalDevHost("http://192.168.1.5:8080"))
        assertTrue(NativeApiResolver.isLocalDevHost("http://192.168.0.10:8080"))
    }

    @Test
    fun isLocalDevHost_treatsPublicOracleIpAsNonLocal() {
        assertFalse(NativeApiResolver.isLocalDevHost("http://80.225.196.181"))
        assertFalse(NativeApiResolver.isLocalDevHost("https://api.example.com"))
    }
}
