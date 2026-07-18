package com.childmonitor.tamper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class OfflineParentPinVerifierTest {

    @Test
    fun matches_acceptsCorrectPin() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val material = OfflineParentPinVerifier.createForTest("ParentPass99", salt)
        assertTrue(OfflineParentPinVerifier.matches("ParentPass99", material))
        assertFalse(OfflineParentPinVerifier.matches("wrong", material))
    }
}
