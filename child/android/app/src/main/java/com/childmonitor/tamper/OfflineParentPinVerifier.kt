package com.childmonitor.tamper

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 verifier matching backend ParentOfflinePinHasher for offline parent-password unlock.
 */
object OfflineParentPinVerifier {

    const val DEFAULT_ITERATIONS = 120_000

    data class Material(
        val saltBase64: String,
        val hashBase64: String,
        val iterations: Int,
    )

    fun matches(pin: String, material: Material): Boolean {
        if (pin.isBlank() || material.iterations < 1) return false
        return try {
            val salt = Base64.getDecoder().decode(material.saltBase64)
            val expected = Base64.getDecoder().decode(material.hashBase64)
            val actual = derive(pin, salt, material.iterations)
            expected.contentEquals(actual)
        } catch (_: Exception) {
            false
        }
    }

    /** Test helper — create material the same way the server does. */
    fun createForTest(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): Material {
        val hash = derive(pin, salt, iterations)
        return Material(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            iterations = iterations,
        )
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } catch (e: GeneralSecurityException) {
            throw e
        } finally {
            spec.clearPassword()
        }
    }
}
