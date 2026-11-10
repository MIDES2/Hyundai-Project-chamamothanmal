package io.chamamothanmal.vehiclestate.source

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import io.chamamothanmal.vehiclestate.DoorStatus
import io.chamamothanmal.vehiclestate.GearPosition
import io.chamamothanmal.vehiclestate.source.PleosVehicleSource.DoorValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosSourceMappingTest {

    // ── 기어 ──

    @Test
    fun onlyParkIsParked() {
        VehicleGear.entries.forEach { gear ->
            val expected = when (gear) {
                VehicleGear.GEAR_PARK -> GearPosition.PARK
                VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
                else -> GearPosition.NOT_PARK
            }
            assertEquals(gear.name, expected, PleosVehicleSource.toGearPosition(gear))
        }
    }

    @Test
    fun nullGearIsUnknown() {
        assertEquals(GearPosition.UNKNOWN, PleosVehicleSource.toGearPosition(null))
    }

    // ── 도어 합치기 ──

    private fun dv(name: String, position: Int?, receivedAt: Long = 100, closed: Int = 0) =
        DoorValue(name, position, closed, receivedAt)

    @Test
    fun allClosedIsClosed() {
        val r = PleosVehicleSource.combineDoors(listOf(dv("L", 0, 100), dv("R", 0, 90)))
        assertEquals(DoorStatus.CLOSED, r.status)
        assertEquals("가장 오래된 값 기준", 90L, r.elapsedRealtimeMs)
    }

    @Test
    fun anyOpenIsOpen() {
        val r = PleosVehicleSource.combineDoors(listOf(dv("L", 0), dv("R", 30), dv("B", null)))
        assertEquals(DoorStatus.OPEN, r.status)
        assertEquals(listOf("R"), r.openDoors)
    }

    @Test
    fun missingValueIsUnknown() {
        assertEquals(DoorStatus.UNKNOWN, PleosVehicleSource.combineDoors(listOf(dv("L", 0), dv("R", null))).status)
    }

    @Test
    fun neverReceivedIsUnknownAndStale() {
        val r = PleosVehicleSource.combineDoors(listOf(dv("L", 0), dv("R", 0, receivedAt = 0)))
        assertEquals(DoorStatus.UNKNOWN, r.status)
        assertEquals(0L, r.elapsedRealtimeMs)
    }

    @Test
    fun noDoorsIsUnknown() {
        assertEquals(DoorStatus.UNKNOWN, PleosVehicleSource.combineDoors(emptyList()).status)
    }

    @Test
    fun usesCapabilityMinimumAsClosed() {
        assertEquals(DoorStatus.CLOSED, PleosVehicleSource.combineDoors(listOf(dv("L", 5, closed = 5))).status)
        assertEquals(DoorStatus.OPEN, PleosVehicleSource.combineDoors(listOf(dv("L", 6, closed = 5))).status)
    }

    // ── 내비 좌표 ──

    @Test
    fun zeroCoordinateMeansNoDestination() {
        assertFalse(PleosNaviSource.isValidCoordinate(0.0, 0.0))
        assertFalse(PleosNaviSource.isValidCoordinate(Double.NaN, 127.0))
        assertFalse(PleosNaviSource.isValidCoordinate(91.0, 127.0))
        assertTrue(PleosNaviSource.isValidCoordinate(37.0, 127.0))
    }
}
