package io.chamamothanmal.client

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Blocking I/O: Android callers must use Dispatchers.IO or an Executor, never the UI thread.
 * Own token per instance/account. No SDK state or automatic playback is inferred here.
 */
class VoiceMessageClient(baseUrl: String, private val tokenProvider: () -> String,
    private val vehicleTokenProvider: () -> String? = { null }) {
    private val base = baseUrl.trimEnd('/')
    init {
        val uri = URI(base)
        require(uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty().isEmpty())
    }
    data class PlaybackEvent(
        val eventId: String, val status: String, val expectedVersion: Long,
        val source: String, val reason: String? = null
    ) {
        fun json() = JSONObject().put("eventId", eventId).put("status", status)
            .put("expectedVersion", expectedVersion).put("source", source).apply {
                reason?.let { put("reason", it) }
            }
    }
    class ApiFailure(val status: Int, val code: String) : Exception("API request failed: HTTP $status ($code)")

    internal fun request(method: String, path: String, contentType: String? = null,
                        body: ByteArray? = null, key: String? = null): ByteArray {
        val token = tokenProvider()
        require(token.matches(Regex("[A-Za-z0-9_.-]{32,16376}"))) { "Missing or invalid local test token" }
        val connection = URI(base + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            // Never follow a redirect with a bearer token to another host.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            vehicleTokenProvider()?.let {
                require(it.matches(Regex("[A-Za-z0-9_-]{43}")))
                connection.setRequestProperty("X-Vehicle-Token", it)
            }
            key?.let { connection.setRequestProperty("Idempotency-Key", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: byteArrayOf()
            if (status !in 200..299) {
                val code = runCatching { JSONObject(String(bytes, Charsets.UTF_8))
                    .getJSONObject("error").getString("code") }.getOrDefault("HTTP_ERROR")
                throw ApiFailure(status, code)
            }
            return bytes
        } finally { connection.disconnect() }
    }
}
