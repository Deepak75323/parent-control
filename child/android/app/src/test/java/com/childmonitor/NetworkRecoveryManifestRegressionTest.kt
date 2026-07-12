package com.childmonitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P1-03: Guard against reintroducing the obsolete CONNECTIVITY_ACTION
 * manifest receiver (dead on Android 10+ / targetSdk ≥ 29).
 */
class NetworkRecoveryManifestRegressionTest {

    @Test
    fun manifest_doesNotDeclareConnectivityActionReceiver() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifest.exists())
        val text = manifest.readText()
        assertFalse(
            "P1-03: CONNECTIVITY_ACTION must not be registered in the manifest",
            text.contains("android.net.conn.CONNECTIVITY_ACTION"),
        )
        assertFalse(
            "P1-03: NetworkRecoveryReceiver must be removed from the manifest",
            text.contains("NetworkRecoveryReceiver"),
        )
        assertTrue(
            "P1-03 comment / NetworkRecoveryMonitor reference should remain for maintainers",
            text.contains("NetworkRecoveryMonitor") || text.contains("P1-03"),
        )
    }
}
