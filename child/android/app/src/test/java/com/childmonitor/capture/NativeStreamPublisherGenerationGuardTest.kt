package com.childmonitor.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStreamPublisherGenerationGuardTest {

    @Test
    fun activeGenerationRequiresRunningMatchingSessionAndGeneration() {
        assertTrue(
            NativeStreamPublisher.isActiveGenerationForTesting(
                generation = 7L,
                expectedSessionId = "sess-1",
                runningValue = true,
                activeSessionIdValue = "sess-1",
                currentGeneration = 7L,
            ),
        )

        assertFalse(
            NativeStreamPublisher.isActiveGenerationForTesting(
                generation = 7L,
                expectedSessionId = "sess-1",
                runningValue = false,
                activeSessionIdValue = "sess-1",
                currentGeneration = 7L,
            ),
        )

        assertFalse(
            NativeStreamPublisher.isActiveGenerationForTesting(
                generation = 7L,
                expectedSessionId = "sess-1",
                runningValue = true,
                activeSessionIdValue = "sess-2",
                currentGeneration = 7L,
            ),
        )

        assertFalse(
            NativeStreamPublisher.isActiveGenerationForTesting(
                generation = 7L,
                expectedSessionId = "sess-1",
                runningValue = true,
                activeSessionIdValue = "sess-1",
                currentGeneration = 8L,
            ),
        )
    }

    @Test
    fun blankExpectedSessionOnlyDependsOnRunningAndGeneration() {
        assertTrue(
            NativeStreamPublisher.isActiveGenerationForTesting(
                generation = 11L,
                expectedSessionId = null,
                runningValue = true,
                activeSessionIdValue = "other-session",
                currentGeneration = 11L,
            ),
        )

        assertFalse(
            NativeStreamPublisher.isActiveGenerationForTesting(
                generation = 11L,
                expectedSessionId = "",
                runningValue = true,
                activeSessionIdValue = "other-session",
                currentGeneration = 12L,
            ),
        )
    }
}
