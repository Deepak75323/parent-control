package com.childmonitor.sync

import android.content.Context
import android.util.Log
import com.childmonitor.MonitoringPrefs
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import com.childmonitor.constants.ApiPaths

object DeviceHttpClient {

    private const val TAG = "DeviceHttpClient"

    sealed class HttpResult {
        data class Ok(val body: String) : HttpResult()
        /** No API base URL or device token — not a network outage. */
        data object NotReady : HttpResult()
        data class HttpError(val code: Int, val errorBody: String?) : HttpResult()
        data class NetworkFailure(val cause: Exception) : HttpResult()
        /** Device has no active network connection. */
        data object NoNetwork : HttpResult()
    }

    /**
     * Check if device has active network connectivity.
     * Returns true if there's an available network connection.
     */
    private fun hasActiveNetwork(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.isConnected == true
        }
    }

    fun get(context: Context, path: String): String? {
        return when (val result = getDetailed(context, path)) {
            is HttpResult.Ok -> result.body
            else -> null
        }
    }

    fun getDetailed(context: Context, path: String): HttpResult {
        // Check network connectivity before attempting request
        if (!hasActiveNetwork(context)) {
            Log.w(TAG, "GET $path skipped - no network connectivity")
            return HttpResult.NoNetwork
        }

        val base = NativeApiResolver.resolveWorkingBase(context) ?: return HttpResult.NotReady
        val token = MonitoringPrefs.deviceToken(context) ?: return HttpResult.NotReady
        val urlString = "$base$path"
        return try {
            val conn = openConnection(urlString, "GET", token, context, null)
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.w(TAG, "GET $path failed: $code $err")
                HttpResult.HttpError(code, unwrapMaybe(err))
            } else {
                val body = conn.inputStream.bufferedReader().readText()
                HttpResult.Ok(ApiPayloadCrypto.unwrapResponseBody(body))
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path error", e)
            HttpResult.NetworkFailure(e)
        }
    }

    fun postJson(context: Context, path: String, jsonBody: String): Boolean {
        return postJsonDetailed(context, path, jsonBody) is HttpResult.Ok
    }

    fun postJsonResponse(context: Context, path: String, jsonBody: String): String? {
        return when (val result = postJsonDetailed(context, path, jsonBody)) {
            is HttpResult.Ok -> result.body
            else -> null
        }
    }

    fun postJsonDetailed(context: Context, path: String, jsonBody: String): HttpResult {
        // Check network connectivity before attempting request
        if (!hasActiveNetwork(context)) {
            Log.w(TAG, "POST $path skipped - no network connectivity")
            return HttpResult.NoNetwork
        }

        val base = NativeApiResolver.resolveWorkingBase(context) ?: return HttpResult.NotReady
        val token = MonitoringPrefs.deviceToken(context) ?: return HttpResult.NotReady
        val urlString = "$base$path"
        val wireBody = ApiPayloadCrypto.wrapRequestBody(jsonBody)
        return try {
            val conn = openConnection(urlString, "POST", token, context, wireBody)
            conn.outputStream.use { it.write(wireBody.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                Log.w(TAG, "POST $path failed: $code $err")
                HttpResult.HttpError(code, unwrapMaybe(err))
            } else {
                val body = conn.inputStream.bufferedReader().readText()
                HttpResult.Ok(ApiPayloadCrypto.unwrapResponseBody(body))
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $path error", e)
            HttpResult.NetworkFailure(e)
        }
    }
    /** Returns true when the server transitioned PENDING → DELIVERED for this command. */
    fun claimCommand(context: Context, commandId: String): Boolean {
        val base = NativeApiResolver.resolveWorkingBase(context) ?: return false
        val token = MonitoringPrefs.deviceToken(context) ?: return false
        val path = ApiPaths.commandClaim(commandId)
        val urlString = "$base$path"
        val wireBody = ApiPayloadCrypto.wrapRequestBody("{}")
        return try {
            val conn = openConnection(urlString, "POST", token, context, wireBody)
            conn.outputStream.use { it.write(wireBody.toByteArray(StandardCharsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "claim $commandId failed: ${conn.responseCode}")
                return false
            }
            val body = ApiPayloadCrypto.unwrapResponseBody(conn.inputStream.bufferedReader().readText())
            val root = org.json.JSONObject(body)
            root.optBoolean("data", false)
        } catch (e: Exception) {
            Log.e(TAG, "claim $commandId error", e)
            false
        }
    }

    private fun unwrapMaybe(err: String?): String? =
        err?.let { ApiPayloadCrypto.unwrapResponseBody(it) }

    /** Returns true when the server recorded the execution fingerprint. */
    fun recordCommandExecution(
        context: Context,
        commandId: String,
        success: Boolean,
        error: String?,
    ): Boolean {
        val body = org.json.JSONObject().put("success", success)
        if (error != null) body.put("errorMessage", error)
        return postJson(context, ApiPaths.commandExecuted(commandId), body.toString())
    }

    fun reportStreamFailure(context: Context, sessionId: String, reason: String): Boolean {
        val body = org.json.JSONObject().put("reason", reason).toString()
        return postJson(context, ApiPaths.streamSessionFailure(sessionId), body)
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
        val idx = urlString.indexOf(ApiPaths.V1_PREFIX)
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
