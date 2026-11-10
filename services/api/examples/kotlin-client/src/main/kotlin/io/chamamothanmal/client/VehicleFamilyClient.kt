package io.chamamothanmal.client

import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

/** Final v3 proposal /api/v2. Blocking I/O: run on a background thread in Android. */
class VehicleFamilyClient(baseUrl: String, tokenProvider: () -> String,
    vehicleTokenProvider: () -> String? = { null }) {
    private val http = VoiceMessageClient(baseUrl, tokenProvider, vehicleTokenProvider)
    private fun id(value: String) = UUID.fromString(value).toString()
    private fun call(method: String, path: String, data: JSONObject? = null): JSONObject {
        val bytes = http.request(method, "/api/v2$path", if(data == null) null else "application/json",
            data?.toString()?.toByteArray(Charsets.UTF_8))
        return if(bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
    }
    fun me() = call("GET", "/me")
    fun createFamily(name: String) = call("POST", "/families", JSONObject().put("name", name))
    fun invite(familyId: String) = call("POST", "/families/${id(familyId)}/invitations", JSONObject())
    fun join(code: String) = call("POST", "/families/join", JSONObject().put("code", code))
    fun createVehicle(familyId: String, name: String) = call("POST", "/vehicles", JSONObject().put("familyId", id(familyId)).put("name", name))
    fun pairingCode(vehicleId: String) = call("POST", "/vehicles/${id(vehicleId)}/pairing-codes", JSONObject())
    fun pair(code: String) = call("POST", "/vehicles/pair", JSONObject().put("code", code))
    fun access(vehicleId: String) = call("GET", "/vehicles/${id(vehicleId)}/access")
    fun candidates(vehicleId: String) = call("GET", "/vehicles/${id(vehicleId)}/messages")
    fun sentDetail(messageId: String) = call("GET", "/messages/${id(messageId)}")
    fun session(sessionId: String) = call("GET", "/sessions/${id(sessionId)}")
    /** Keep requestId and body unchanged on retry. Only call after actual pre-departure consent. */
    fun start(vehicleId: String, requestId: String, messageId: String, latitude: Double, longitude: Double,
        consent: Boolean, parkedConfirmed: Boolean, source: String) = call("POST", "/vehicles/${id(vehicleId)}/sessions",
        JSONObject().put("requestId", id(requestId)).put("consent", consent).put("parkedConfirmed", parkedConfirmed)
            .put("destinationLatitude", latitude).put("destinationLongitude", longitude).put("source", source)
            .put("messageIds", JSONArray().put(id(messageId))))
    fun delivery(sessionId: String, messageId: String) = call("GET",
        "/sessions/${id(sessionId)}/messages/${id(messageId)}")
    fun download(sessionId: String, messageId: String): ByteArray = http.request("GET",
        "/api/v2/sessions/${id(sessionId)}/messages/${id(messageId)}/audio")
    fun report(sessionId: String, messageId: String, event: VoiceMessageClient.PlaybackEvent) = call("POST",
        "/sessions/${id(sessionId)}/messages/${id(messageId)}/events", event.json())
    fun reply(sessionId: String, messageId: String, replyId: String, kind: String, parkedConfirmed: Boolean) = call("POST",
        "/sessions/${id(sessionId)}/messages/${id(messageId)}/replies",
        JSONObject().put("replyId", id(replyId)).put("kind", kind).put("parkedConfirmed", parkedConfirmed))
    fun end(sessionId: String, reason: String) = call("POST", "/sessions/${id(sessionId)}/end", JSONObject().put("reason", reason))
    fun disconnect(vehicleId: String) = call("DELETE", "/vehicles/${id(vehicleId)}/connection")
    fun deleteMessage(messageId: String) = call("DELETE", "/messages/${id(messageId)}")
    fun register(audio: File, vehicleId: String, placeName: String, latitude: Double, longitude: Double,
        requestId: String, radiusMeters: Int = 100, expiresInHours: Int = 24, address: String = ""): String {
        require(audio.length() in 1..5_242_880)
        val boundary = "voice-${UUID.randomUUID()}"
        val bytes = java.io.ByteArrayOutputStream()
        fun write(value: String) = bytes.write(value.toByteArray(Charsets.UTF_8))
        for((key, value) in linkedMapOf("vehicleId" to id(vehicleId), "placeName" to placeName, "address" to address,
            "latitude" to latitude, "longitude" to longitude, "radiusMeters" to radiusMeters, "expiresInHours" to expiresInHours)) {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n")
        }
        write("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"voice.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
        audio.inputStream().use { it.copyTo(bytes) }; write("\r\n--$boundary--\r\n")
        val result = http.request("POST", "/api/v2/messages", "multipart/form-data; boundary=$boundary", bytes.toByteArray(), id(requestId))
        return JSONObject(String(result, Charsets.UTF_8)).getString("messageId")
    }
}
