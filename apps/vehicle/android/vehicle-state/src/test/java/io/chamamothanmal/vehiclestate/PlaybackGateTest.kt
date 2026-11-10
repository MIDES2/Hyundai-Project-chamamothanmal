package io.chamamothanmal.vehiclestate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGateTest {

    private val now = 10_000_000L
    // 합성 좌표(실사용자 위치 아님)
    private val destination = Destination(latitude = 37.0, longitude = 127.0, radiusMeters = 100)

    private fun gear(position: GearPosition = GearPosition.PARK, ageMs: Long = 0, source: SignalSource = SignalSource.SDK) =
        GearReading(position, position.name, source, now - ageMs)

    private fun door(status: DoorStatus = DoorStatus.CLOSED, ageMs: Long = 0, source: SignalSource = SignalSource.SDK) =
        DoorReading(status, if (status == DoorStatus.OPEN) listOf("ROW_1_LEFT") else emptyList(), source, now - ageMs)

    private fun location(
        lat: Double = 37.0,
        lon: Double = 127.0,
        ageMs: Long = 0,
        accuracy: Float? = 5f,
        source: SignalSource = SignalSource.SDK,
    ) = LocationReading(lat, lon, accuracy, source, now - ageMs)

    private fun navi(
        lat: Double? = 37.0,
        lon: Double? = 127.0,
        arrivedAgoMs: Long? = null,
        source: SignalSource = SignalSource.SDK,
    ) = NaviState(lat, lon, arrivedAgoMs?.let { now - it }, source, now)

    /** 기본값: P단, 도어 닫힘, 목적지 위 새 GPS, 내비 없음 */
    private fun snapshot(
        gear: GearReading? = gear(),
        door: DoorReading? = door(),
        location: LocationReading? = location(),
        navi: NaviState? = null,
        locationError: String? = null,
    ) = VehicleSnapshot(gear = gear, location = location, door = door, navi = navi, locationError = locationError)

    private fun evaluate(snapshot: VehicleSnapshot, policy: GatePolicy = GatePolicy()) =
        PlaybackGate.evaluate(snapshot, destination, now, policy)

    private fun assertHold(expected: HoldReason, decision: PlaybackDecision) {
        assertTrue("보류여야 함: $decision", decision is PlaybackDecision.Hold)
        assertEquals(expected, (decision as PlaybackDecision.Hold).reason)
    }

    private fun assertAllow(decision: PlaybackDecision, arrival: ArrivalEvidence, source: SignalSource = SignalSource.SDK) {
        assertTrue("허용이어야 함: $decision", decision is PlaybackDecision.Allow)
        decision as PlaybackDecision.Allow
        assertEquals(arrival, decision.arrival)
        assertEquals(source, decision.source)
    }

    // ── 정상 ──

    @Test
    fun allowsWhenParkedDoorsClosedAndInsideRadiusByGps() {
        assertAllow(evaluate(snapshot()), ArrivalEvidence.GPS)
    }

    // ── 기어 ──

    @Test
    fun holdsWhenNothingReceived() {
        assertHold(HoldReason.GEAR_UNKNOWN, evaluate(VehicleSnapshot()))
    }

    @Test
    fun holdsWhenGearUnknownValue() {
        assertHold(HoldReason.GEAR_UNKNOWN, evaluate(snapshot(gear = gear(GearPosition.UNKNOWN))))
    }

    @Test
    fun holdsWhenGearStale() {
        assertHold(HoldReason.GEAR_STALE, evaluate(snapshot(gear = gear(ageMs = 10_001))))
    }

    @Test
    fun allowsGearExactlyAtMaxAge() {
        assertAllow(evaluate(snapshot(gear = gear(ageMs = 10_000))), ArrivalEvidence.GPS)
    }

    @Test
    fun holdsWhenGearTimestampIsInFuture() {
        assertHold(HoldReason.GEAR_STALE, evaluate(snapshot(gear = gear(ageMs = -1))))
    }

    @Test
    fun holdsAsDrivingWhenNotParked() {
        val decision = evaluate(snapshot(gear = gear(GearPosition.NOT_PARK)))
        assertHold(HoldReason.NOT_PARKED, decision)
        assertEquals("DRIVING", (decision as PlaybackDecision.Hold).reason.contractReason)
    }

    @Test
    fun drivingTakesPriorityOverMissingLocationAndDoor() {
        assertHold(HoldReason.NOT_PARKED, evaluate(snapshot(gear = gear(GearPosition.NOT_PARK), door = null, location = null)))
    }

    // ── 도어 ──

    @Test
    fun holdsWhenDoorMissing() {
        assertHold(HoldReason.DOOR_UNKNOWN, evaluate(snapshot(door = null)))
    }

    @Test
    fun holdsWhenDoorOpen() {
        val decision = evaluate(snapshot(door = door(DoorStatus.OPEN)))
        assertHold(HoldReason.DOOR_OPEN, decision)
        assertEquals("DOOR_OPEN", (decision as PlaybackDecision.Hold).reason.contractReason)
    }

    @Test
    fun openDoorReportedEvenIfStale() {
        assertHold(HoldReason.DOOR_OPEN, evaluate(snapshot(door = door(DoorStatus.OPEN, ageMs = 60_000))))
    }

    @Test
    fun holdsWhenDoorStale() {
        assertHold(HoldReason.DOOR_STALE, evaluate(snapshot(door = door(ageMs = 10_001))))
    }

    @Test
    fun holdsWhenDoorPartlyUnknown() {
        assertHold(HoldReason.DOOR_UNKNOWN, evaluate(snapshot(door = door(DoorStatus.UNKNOWN))))
    }

    @Test
    fun doorCheckCanBeDisabled() {
        assertAllow(evaluate(snapshot(door = null), GatePolicy(requireDoorClosed = false)), ArrivalEvidence.GPS)
    }

    // ── 위치 (GPS) ──

    @Test
    fun holdsWhenLocationMissing() {
        val decision = evaluate(snapshot(location = null, locationError = "위치 권한 없음"))
        assertHold(HoldReason.LOCATION_UNKNOWN, decision)
        assertEquals("위치 권한 없음", (decision as PlaybackDecision.Hold).detail)
    }

    @Test
    fun holdsWhenLocationStale() {
        assertHold(HoldReason.LOCATION_STALE, evaluate(snapshot(location = location(ageMs = 15_001))))
    }

    @Test
    fun holdsWhenLocationTimestampIsInFuture() {
        assertHold(HoldReason.LOCATION_STALE, evaluate(snapshot(location = location(ageMs = -5))))
    }

    @Test
    fun holdsWhenLocationInaccurate() {
        assertHold(HoldReason.LOCATION_INACCURATE, evaluate(snapshot(location = location(accuracy = 150f))))
    }

    @Test
    fun holdsWhenLocationAccuracyIsNaN() {
        assertHold(HoldReason.LOCATION_INACCURATE, evaluate(snapshot(location = location(accuracy = Float.NaN))))
    }

    @Test
    fun holdsWhenCoordinatesInvalid() {
        assertHold(HoldReason.LOCATION_UNKNOWN, evaluate(snapshot(location = location(lat = Double.NaN))))
    }

    @Test
    fun notArrivedHasNoContractReason() {
        // 위도 0.002도 ≈ 222m
        val decision = evaluate(snapshot(location = location(lat = 37.002)))
        assertHold(HoldReason.NOT_ARRIVED, decision)
        assertNull((decision as PlaybackDecision.Hold).reason.contractReason)
    }

    @Test
    fun allowsJustInsideRadius() {
        // 위도 0.0008도 ≈ 89m
        assertAllow(evaluate(snapshot(location = location(lat = 37.0008))), ArrivalEvidence.GPS)
    }

    // ── 내비 ──

    @Test
    fun allowsWhenNaviDestinationMatches() {
        assertAllow(evaluate(snapshot(navi = navi(lat = 37.0005))), ArrivalEvidence.GPS)
    }

    @Test
    fun holdsWhenNaviDestinationDiffers() {
        val decision = evaluate(snapshot(navi = navi(lat = 37.01)))
        assertHold(HoldReason.DESTINATION_MISMATCH, decision)
        assertNull((decision as PlaybackDecision.Hold).reason.contractReason)
    }

    @Test
    fun naviWithoutDestinationIsIgnored() {
        assertAllow(evaluate(snapshot(navi = navi(lat = null, lon = null))), ArrivalEvidence.GPS)
    }

    @Test
    fun naviArrivalCoversMissingGps() {
        val decision = evaluate(snapshot(location = null, navi = navi(arrivedAgoMs = 60_000)))
        assertAllow(decision, ArrivalEvidence.NAVI)
        assertNull((decision as PlaybackDecision.Allow).distanceMeters)
    }

    @Test
    fun naviArrivalCoversStaleGps() {
        assertAllow(evaluate(snapshot(location = location(ageMs = 60_000), navi = navi(arrivedAgoMs = 1_000))), ArrivalEvidence.NAVI)
    }

    @Test
    fun freshGpsOutsideRadiusOverridesNaviArrival() {
        // 신호가 엇갈리면 보류
        val decision = evaluate(snapshot(location = location(lat = 37.002), navi = navi(arrivedAgoMs = 1_000)))
        assertHold(HoldReason.NOT_ARRIVED, decision)
    }

    @Test
    fun naviArrivalTooOldIsIgnored() {
        val decision = evaluate(snapshot(location = null, navi = navi(arrivedAgoMs = 30 * 60_000L + 1)))
        assertHold(HoldReason.LOCATION_UNKNOWN, decision)
    }

    @Test
    fun naviArrivalForOtherDestinationIsIgnored() {
        // 목적지가 다르면 도착 여부와 상관없이 불일치로 보류
        val decision = evaluate(snapshot(location = null, navi = navi(lat = 37.01, arrivedAgoMs = 1_000)))
        assertHold(HoldReason.DESTINATION_MISMATCH, decision)
    }

    @Test
    fun naviWithoutArrivalDoesNotCoverMissingGps() {
        assertHold(HoldReason.LOCATION_UNKNOWN, evaluate(snapshot(location = null, navi = navi())))
    }

    // ── MOCK ──

    @Test
    fun rejectsMockGearByDefault() {
        assertHold(HoldReason.MOCK_NOT_ALLOWED, evaluate(snapshot(gear = gear(source = SignalSource.MOCK))))
    }

    @Test
    fun rejectsMockDoorByDefault() {
        assertHold(HoldReason.MOCK_NOT_ALLOWED, evaluate(snapshot(door = door(source = SignalSource.MOCK))))
    }

    @Test
    fun rejectsMockLocationByDefault() {
        assertHold(HoldReason.MOCK_NOT_ALLOWED, evaluate(snapshot(location = location(source = SignalSource.MOCK))))
    }

    @Test
    fun rejectsMockNaviArrivalByDefault() {
        val s = snapshot(location = null, navi = navi(arrivedAgoMs = 1_000, source = SignalSource.MOCK))
        assertHold(HoldReason.MOCK_NOT_ALLOWED, evaluate(s))
    }

    @Test
    fun mockAllowedIsReportedAsMock() {
        val decision = evaluate(snapshot(gear = gear(source = SignalSource.MOCK)), GatePolicy(allowMock = true))
        assertAllow(decision, ArrivalEvidence.GPS, SignalSource.MOCK)
    }

    // ── 거리 ──

    @Test
    fun distanceIsReasonable() {
        // 위도 1도 ≈ 111.2km
        val d = PlaybackGate.distanceMeters(37.0, 127.0, 38.0, 127.0)
        assertTrue("거리 $d", d in 111_000.0..111_400.0)
    }
}
