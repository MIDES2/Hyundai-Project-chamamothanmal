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
    val base = args.firstOrNull() ?: "http://127.0.0.1:8000"
    var vehicleToken: String? = null
    val sender = VehicleFamilyClient(base, { value("API_SENDER_TOKEN") })
    val driver = VehicleFamilyClient(base, { value("API_RECIPIENT_TOKEN") }, { vehicleToken })
    val outsider = VehicleFamilyClient(base, { value("API_OTHER_RECIPIENT_TOKEN") })
    val family = sender.createFamily("Synthetic Kotlin family").getString("familyId")
    driver.join(sender.invite(family).getString("code"))
    val vehicle = sender.createVehicle(family, "Synthetic car").getString("vehicleId")
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
