package com.childmonitor.sync

import com.childmonitor.BuildConfig
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Airtel-compatible AES-CBC wire encryption.
 * Key/IV from BuildConfig (Gradle env at build time) — never hardcode in source.
 * Wire format: {"data":"<Base64 ciphertext>"}.
 */
object ApiPayloadCrypto {

    private const val TRANSFORM = "AES/CBC/PKCS5Padding"

    fun isConfigured(): Boolean =
        BuildConfig.API_PAYLOAD_ENCRYPTION_KEY.isNotBlank() &&
            BuildConfig.API_PAYLOAD_ENCRYPTION_IV.isNotBlank()

    fun wrapRequestBody(plaintextJson: String): String {
        if (!isConfigured()) return plaintextJson
        val encrypted = encrypt(plaintextJson)
        return JSONObject().put("data", encrypted).toString()
    }

    fun unwrapResponseBody(wireBody: String): String {
        if (!isConfigured()) return wireBody
        val trimmed = wireBody.trim()
        if (!trimmed.startsWith("{")) return wireBody
        return try {
            val root = JSONObject(trimmed)
            if (root.length() == 1 && root.has("data") && root.opt("data") is String) {
                decrypt(root.getString("data"))
            } else {
                wireBody
            }
        } catch (_: Exception) {
            wireBody
        }
    }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(), ivSpec())
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decrypt(ciphertextBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec(), ivSpec())
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64.trim()))
        var text = String(decrypted, StandardCharsets.UTF_8)
        val lastBrace = text.lastIndexOf('}')
        if (lastBrace >= 0) {
            text = text.substring(0, lastBrace + 1)
        }
        return text
    }

    private fun keySpec() =
        SecretKeySpec(
            BuildConfig.API_PAYLOAD_ENCRYPTION_KEY.toByteArray(StandardCharsets.UTF_8),
            "AES",
        )

    private fun ivSpec() =
        IvParameterSpec(BuildConfig.API_PAYLOAD_ENCRYPTION_IV.toByteArray(StandardCharsets.UTF_8))
}
