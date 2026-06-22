package com.childmonitor.sync

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object DeviceHttpClient {

    private const val TAG = "DeviceHttpClient"

    fun get(context: Context, path: String): String? {
        val base = NativeApiResolver.resolveWorkingBase(context) ?: return null
        val token = MonitoringPrefs.deviceToken(context) ?: return null
        val urlString = "$base$path"
        return try {
            val conn = openConnection(urlString, "GET", token, context, null)
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "GET $path failed: ${conn.responseCode}")
                null
            } else {
                conn.inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path error", e)
            null
        }
    }

    fun postJson(context: Context, path: String, jsonBody: String): Boolean {
        val base = NativeApiResolver.resolveWorkingBase(context) ?: return false
        val token = MonitoringPrefs.deviceToken(context) ?: return false
        val urlString = "$base$path"
        return try {
            val conn = openConnection(urlString, "POST", token, context, jsonBody)
            conn.outputStream.use { it.write(jsonBody.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.w(TAG, "POST $path failed: $code $err")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $path error", e)
            false
        }
    }

    /** Returns true when the server transitioned PENDING → DELIVERED for this command. */
    fun claimCommand(context: Context, commandId: String): Boolean {
        val base = NativeApiResolver.resolveWorkingBase(context) ?: return false
        val token = MonitoringPrefs.deviceToken(context) ?: return false
        val path = "/api/v1/device/commands/$commandId/claim"
        val urlString = "$base$path"
        return try {
            val conn = openConnection(urlString, "POST", token, context, "{}")
            conn.outputStream.use { it.write("{}".toByteArray(StandardCharsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "claim $commandId failed: ${conn.responseCode}")
                return false
            }
            val body = conn.inputStream.bufferedReader().readText()
            val root = org.json.JSONObject(body)
            root.optBoolean("data", false)
        } catch (e: Exception) {
            Log.e(TAG, "claim $commandId error", e)
            false
        }
    }

    /** Returns true when the server recorded the execution fingerprint. */
    fun recordCommandExecution(
        context: Context,
        commandId: String,
        success: Boolean,
        error: String?,
    ): Boolean {
        val body = org.json.JSONObject().put("success", success)
        if (error != null) body.put("errorMessage", error)
        return postJson(context, "/api/v1/device/commands/$commandId/executed", body.toString())
    }

    fun reportStreamFailure(context: Context, sessionId: String, reason: String): Boolean {
        val body = org.json.JSONObject().put("reason", reason).toString()
        return postJson(context, "/api/v1/device/stream-sessions/$sessionId/failure", body)
    }

    private fun openConnection(
        urlString: String,
        method: String,
        bearerToken: String,
        context: Context,
        body: String?,
    ): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        conn.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            conn.doOutput = true
            applyHmacHeaders(conn, method, extractPath(urlString), body, context)
        }
        return conn
    }

    private fun applyHmacHeaders(
        conn: HttpURLConnection,
        method: String,
        path: String,
        body: String,
        context: Context,
    ) {
        val secret = MonitoringPrefs.deviceHmacKey(context) ?: return
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString()
        val bodyHash = sha256Hex(body)
        val canonical = "${method.uppercase()}\n$path\n$timestamp\n$bodyHash"
        val signature = sign(secret, canonical)
        conn.setRequestProperty("X-Device-Timestamp", timestamp)
        conn.setRequestProperty("X-Device-Nonce", nonce)
        conn.setRequestProperty("X-Device-Signature", signature)
    }

    private fun extractPath(urlString: String): String {
        val idx = urlString.indexOf("/api/v1/")
        return if (idx >= 0) urlString.substring(idx) else urlString
    }

    private fun sign(secret: String, canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return HexFormat.of().formatHex(digest.digest(input.toByteArray(StandardCharsets.UTF_8)))
    }
}
