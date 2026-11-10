package io.chamamothanmal.vehiclestate.source

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import io.chamamothanmal.vehiclestate.GearPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class GearMappingTest {

    @Test
    fun onlyParkIsParked() {
        VehicleGear.entries.forEach { gear ->
            val expected = when (gear) {
                VehicleGear.GEAR_PARK -> GearPosition.PARK
                VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
                else -> GearPosition.NOT_PARK
            }
            assertEquals(gear.name, expected, PleosGearSource.toGearPosition(gear))
        }
    }

    @Test
    fun nullIsUnknown() {
        assertEquals(GearPosition.UNKNOWN, PleosGearSource.toGearPosition(null))
    }
}
