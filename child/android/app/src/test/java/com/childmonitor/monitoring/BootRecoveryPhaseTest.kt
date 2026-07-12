package com.childmonitor.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test

class BootRecoveryPhaseTest {

    @Test
    fun fromWorkInput_parsesKnownPhases() {
        assertEquals(BootRecoveryPhase.FAST, BootRecoveryPhase.fromWorkInput("FAST"))
        assertEquals(BootRecoveryPhase.RECOVERY, BootRecoveryPhase.fromWorkInput("RECOVERY"))
        assertEquals(BootRecoveryPhase.FALLBACK, BootRecoveryPhase.fromWorkInput("FALLBACK"))
    }

    @Test
    fun fromWorkInput_defaultsToFast() {
        assertEquals(BootRecoveryPhase.FAST, BootRecoveryPhase.fromWorkInput(null))
        assertEquals(BootRecoveryPhase.FAST, BootRecoveryPhase.fromWorkInput("unknown"))
    }
}
