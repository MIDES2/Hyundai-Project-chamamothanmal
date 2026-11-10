package com.example.malttuk.integration

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context

/** Unknown platform restrictions fail closed. No mock values are produced here. */
class CarUxGuard(private val context: Context, private val changed: () -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    var allowsInteraction = false; private set
    fun start() {
        stop()
        try {
            val connected = Car.createCar(context) ?: return
            car = connected
            manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            val ux = manager ?: return
            ux.registerListener { restrictions ->
                allowsInteraction = !restrictions.isRequiresDistractionOptimization
                changed()
            }
            allowsInteraction = !ux.currentCarUxRestrictions.isRequiresDistractionOptimization
        } catch(error: Exception) { allowsInteraction = false }
        catch(error: LinkageError) { allowsInteraction = false }
        changed()
    }
    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null; car = null; allowsInteraction = false
    }
}
