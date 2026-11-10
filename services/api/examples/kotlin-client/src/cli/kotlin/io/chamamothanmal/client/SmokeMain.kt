package io.chamamothanmal.client

import java.io.File
import java.util.Properties
import java.util.UUID

/** Synthetic full vehicle-family flow, not actual SDK or speaker playback. */
fun main(args: Array<String>) {
    val config = Properties()
    for(file in listOf(File("../../.env"), File(".env"))) if(file.isFile) file.reader(Charsets.UTF_8).use { config.load(it) }
    fun value(key: String) = System.getenv(key) ?: config.getProperty(key) ?: error("Missing local setting: $key")
    fun uuid() = UUID.randomUUID().toString()
    val prepareOnly = args.firstOrNull() == "--prepare-vehicle-demo"
    val base = args.getOrNull(if (prepareOnly) 1 else 0) ?: "http://127.0.0.1:8000"
    if (prepareOnly) check(java.net.URI(base).host in setOf("127.0.0.1", "localhost")) { "Preparation is local-test-only" }
    var vehicleToken: String? = null
    val sender = VehicleFamilyClient(base, { value("API_SENDER_TOKEN") })
    val driver = VehicleFamilyClient(base, { value("API_RECIPIENT_TOKEN") }, { vehicleToken })
    val outsider = VehicleFamilyClient(base, { value("API_OTHER_RECIPIENT_TOKEN") })
    if (prepareOnly) {
        check(sender.me().getString("userId") == "demo-sender" && driver.me().getString("userId") == "demo-recipient") {
            "Preparation requires local synthetic accounts, not real Supabase accounts"
        }
    }
    val family = sender.createFamily("Synthetic Kotlin family").getString("familyId")
    driver.join(sender.invite(family).getString("code"))
    val vehicle = sender.createVehicle(family, "Synthetic car").getString("vehicleId")
    if (prepareOnly) {
        val file = File("uploads/sample.wav"); require(file.isFile) { "Run make-sample.ps1 first" }
        val mid = sender.register(file, vehicle, "모의 시연 목적지", 37.0, 127.0, uuid(), address = "시험용 좌표 · 실제 장소 아님")
        val code = sender.pairingCode(vehicle)
        val port = java.net.URI(base).port.let { if (it < 0) 80 else it }
        val output = File("build/vehicle-demo-connection.json")
        output.parentFile.mkdirs()
        output.writeText(org.json.JSONObject().put("serverUrlForEmulator", "http://10.0.2.2:$port")
            .put("userAccessToken", value("API_RECIPIENT_TOKEN")).put("pairingCode", code.getString("code"))
            .put("pairingCodeExpiresAt", code.getLong("expiresAt")).put("vehicleId", vehicle).put("messageId", mid)
            .put("source", "MOCK").toString(2), Charsets.UTF_8)
        println("Prepared synthetic vehicle message. Open services/api/build/vehicle-demo-connection.json locally; never share or commit it.")
        println("Pair within 10 minutes. No token or pairing code was printed. No actual SDK playback was tested.")
        return
    }
    vehicleToken = driver.pair(sender.pairingCode(vehicle).getString("code")).getString("vehicleToken")
    driver.access(vehicle)
    val file = File("uploads/sample.wav"); require(file.isFile) { "Run scripts/make-sample.ps1 first" }
    val key = uuid()
    val mid = sender.register(file, vehicle, "Synthetic destination", 37.0, 127.0, key)
    check(sender.register(file, vehicle, "Synthetic destination", 37.0, 127.0, key) == mid)
    check(driver.candidates(vehicle).getJSONArray("items").length() == 1)
    val sid = driver.start(vehicle, uuid(), mid, 37.0, 127.0, true, true, "MOCK").getString("sessionId")
    check(driver.delivery(sid, mid).getJSONObject("playback").getString("status") == "PENDING")
    check(driver.download(sid, mid).contentEquals(file.readBytes()))
    val denied = runCatching { outsider.sentDetail(mid) }.exceptionOrNull()
    check(denied is VoiceMessageClient.ApiFailure && denied.status == 404)
    driver.report(sid, mid, VoiceMessageClient.PlaybackEvent(uuid(), "HELD", 0, "MOCK", "STATE_UNKNOWN"))
    driver.report(sid, mid, VoiceMessageClient.PlaybackEvent(uuid(), "PLAYING", 1, "MOCK", "USER_ACTION"))
    val complete = VoiceMessageClient.PlaybackEvent(uuid(), "COMPLETED", 2, "MOCK")
    check(driver.report(sid, mid, complete).getLong("version") == 3L)
    check(driver.report(sid, mid, complete).getLong("version") == 3L)
    driver.reply(sid, mid, uuid(), "THANK_YOU", true)
    check(sender.sentDetail(mid).getJSONArray("replies").getJSONObject(0).getString("kind") == "THANK_YOU")
    driver.end(sid, "ARRIVED")
    sender.disconnect(vehicle)
    val revoked = runCatching { driver.access(vehicle) }.exceptionOrNull()
    check(revoked is VoiceMessageClient.ApiFailure && revoked.status == 404)
    println("PASS v2: family invitation, vehicle pairing, upload/retry, consent/session, exact audio, isolation, events/retry, reply, sender receipt, disconnect")
    println("Synthetic MOCK reports only; no actual Android UI, SDK or audio playback was tested.")
}
