package io.chamamothanmal.vehiclestate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGateTest {

    private val now = 1_000_000L
    // 합성 좌표(실사용자 위치 아님)
    private val destination = Destination(latitude = 37.0, longitude = 127.0, radiusMeters = 100)

    private fun gear(position: GearPosition, ageMs: Long = 0, source: SignalSource = SignalSource.SDK) =
        GearReading(position, position.name, source, now - ageMs)

    private fun location(
        lat: Double = 37.0,
        lon: Double = 127.0,
        ageMs: Long = 0,
        accuracy: Float? = 5f,
        source: SignalSource = SignalSource.SDK,
    ) = LocationReading(lat, lon, accuracy, source, now - ageMs)

    private fun evaluate(snapshot: VehicleSnapshot, policy: GatePolicy = GatePolicy()) =
        PlaybackGate.evaluate(snapshot, destination, now, policy)

    private fun assertHold(expected: HoldReason, decision: PlaybackDecision) {
        assertTrue("보류여야 함: $decision", decision is PlaybackDecision.Hold)
        assertEquals(expected, (decision as PlaybackDecision.Hold).reason)
    }

    @Test
    fun allowsWhenParkedFreshAndInsideRadius() {
        val decision = evaluate(VehicleSnapshot(gear(GearPosition.PARK), location()))
        assertTrue(decision is PlaybackDecision.Allow)
        assertEquals(SignalSource.SDK, (decision as PlaybackDecision.Allow).source)
    }

    @Test
    fun holdsWhenNothingReceived() {
        assertHold(HoldReason.GEAR_UNKNOWN, evaluate(VehicleSnapshot()))
    }

    @Test
    fun holdsWhenGearUnknownValue() {
        assertHold(HoldReason.GEAR_UNKNOWN, evaluate(VehicleSnapshot(gear(GearPosition.UNKNOWN), location())))
    }

    @Test
    fun holdsWhenGearStale() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK, ageMs = 10_001), location())
        assertHold(HoldReason.GEAR_STALE, evaluate(snapshot))
    }

    @Test
    fun allowsGearExactlyAtMaxAge() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK, ageMs = 10_000), location())
        assertTrue(evaluate(snapshot) is PlaybackDecision.Allow)
    }

    @Test
    fun holdsWhenGearTimestampIsInFuture() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK, ageMs = -1), location())
        assertHold(HoldReason.GEAR_STALE, evaluate(snapshot))
    }

    @Test
    fun holdsAsDrivingWhenNotParked() {
        val decision = evaluate(VehicleSnapshot(gear(GearPosition.NOT_PARK), location()))
        assertHold(HoldReason.NOT_PARKED, decision)
        assertEquals("DRIVING", (decision as PlaybackDecision.Hold).reason.contractReason)
    }

    @Test
    fun holdsWhenLocationMissing() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK), null, locationError = "위치 권한 없음")
        val decision = evaluate(snapshot)
        assertHold(HoldReason.LOCATION_UNKNOWN, decision)
        assertEquals("위치 권한 없음", (decision as PlaybackDecision.Hold).detail)
    }

    @Test
    fun holdsWhenLocationStale() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK), location(ageMs = 15_001))
        assertHold(HoldReason.LOCATION_STALE, evaluate(snapshot))
    }

    @Test
    fun holdsWhenLocationTimestampIsInFuture() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK), location(ageMs = -5))
        assertHold(HoldReason.LOCATION_STALE, evaluate(snapshot))
    }

    @Test
    fun holdsWhenLocationInaccurate() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK), location(accuracy = 150f))
        assertHold(HoldReason.LOCATION_INACCURATE, evaluate(snapshot))
    }

    @Test
    fun holdsWhenLocationAccuracyIsNaN() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK), location(accuracy = Float.NaN))
        assertHold(HoldReason.LOCATION_INACCURATE, evaluate(snapshot))
    }

    @Test
    fun holdsWhenCoordinatesInvalid() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK), location(lat = Double.NaN))
        assertHold(HoldReason.LOCATION_UNKNOWN, evaluate(snapshot))
    }

    @Test
    fun notArrivedHasNoContractReason() {
        // 위도 0.002도 ≈ 222m
        val decision = evaluate(VehicleSnapshot(gear(GearPosition.PARK), location(lat = 37.002)))
        assertHold(HoldReason.NOT_ARRIVED, decision)
        assertNull((decision as PlaybackDecision.Hold).reason.contractReason)
    }

    @Test
    fun allowsJustInsideRadius() {
        // 위도 0.0008도 ≈ 89m
        val decision = evaluate(VehicleSnapshot(gear(GearPosition.PARK), location(lat = 37.0008)))
        assertTrue(decision is PlaybackDecision.Allow)
    }

    @Test
    fun rejectsMockByDefault() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK, source = SignalSource.MOCK), location())
        assertHold(HoldReason.MOCK_NOT_ALLOWED, evaluate(snapshot))
    }

    @Test
    fun mockLocationAlsoRejectedByDefault() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK), location(source = SignalSource.MOCK))
        assertHold(HoldReason.MOCK_NOT_ALLOWED, evaluate(snapshot))
    }

    @Test
    fun mockAllowedIsReportedAsMock() {
        val snapshot = VehicleSnapshot(gear(GearPosition.PARK, source = SignalSource.MOCK), location())
        val decision = evaluate(snapshot, GatePolicy(allowMock = true))
        assertTrue(decision is PlaybackDecision.Allow)
        assertEquals(SignalSource.MOCK, (decision as PlaybackDecision.Allow).source)
    }

    @Test
    fun drivingTakesPriorityOverMissingLocation() {
        val decision = evaluate(VehicleSnapshot(gear(GearPosition.NOT_PARK), null))
        assertHold(HoldReason.NOT_PARKED, decision)
    }

    @Test
    fun distanceIsReasonable() {
        // 위도 1도 ≈ 111.2km
        val d = PlaybackGate.distanceMeters(37.0, 127.0, 38.0, 127.0)
        assertTrue("거리 $d", d in 111_000.0..111_400.0)
    }
}
