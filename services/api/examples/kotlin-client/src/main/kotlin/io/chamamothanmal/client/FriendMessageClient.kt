package io.chamamothanmal.client

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Same authenticated friend contract used by the mobile web; tokens remain in memory. */
class FriendMessageClient(baseUrl: String, token: String) {
    private val transport = VoiceMessageClient(baseUrl, { token })
    private fun call(method: String, path: String, data: JSONObject? = null) = transport.request(method, "/api/v2$path",
        if(data==null) null else "application/json", data?.toString()?.toByteArray(Charsets.UTF_8))
    fun profile() = JSONObject(String(call("GET", "/profile"), Charsets.UTF_8))
    fun inbox() = JSONArray(String(call("GET", "/friend-messages/inbox"), Charsets.UTF_8))
    fun archive() = JSONArray(String(call("GET", "/friend-messages/archive"), Charsets.UTF_8))
    fun audio(id: String): ByteArray = call("GET", "/friend-messages/${java.util.UUID.fromString(id)}/audio")
    fun complete(id: String) = JSONObject(String(call("PUT", "/friend-messages/${java.util.UUID.fromString(id)}/completion"), Charsets.UTF_8))
    fun delete(id: String) { call("DELETE", "/friend-messages/${java.util.UUID.fromString(id)}") }
    fun react(id: String, emoji: String) { call("PUT", "/friend-messages/${java.util.UUID.fromString(id)}/reaction", JSONObject().put("emoji",emoji)) }
    fun logout() { call("POST", "/auth/logout") }
    companion object {
        fun login(baseUrl: String, login: String, password: String): FriendMessageClient {
            val uri=URI(baseUrl.trimEnd('/'))
            require(uri.scheme in setOf("http","https") && uri.host!=null && uri.userInfo==null && uri.rawQuery==null && uri.rawFragment==null && uri.path.orEmpty().isEmpty())
            val connection=URI("$uri/api/v2/auth/login").toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod="POST";connection.connectTimeout=10000;connection.readTimeout=20000;connection.instanceFollowRedirects=false
                val bytes=JSONObject().put("login",login).put("password",password).toString().toByteArray(Charsets.UTF_8)
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val status=connection.responseCode
                if(status !in 200..299) throw VoiceMessageClient.ApiFailure(status,"LOGIN_FAILED")
                val body=connection.inputStream.use { it.readBytes() }
                return FriendMessageClient(uri.toString(),JSONObject(String(body,Charsets.UTF_8)).getString("accessToken"))
            } finally { connection.disconnect() }
        }
    }
}
