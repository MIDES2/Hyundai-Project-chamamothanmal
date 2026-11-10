package io.chamamothanmal.vehiclestate.source

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.chamamothanmal.vehiclestate.GearPosition
import io.chamamothanmal.vehiclestate.GearReading
import io.chamamothanmal.vehiclestate.SignalSource

/**
 * Pleos Vehicle SDK로 기어를 받는다(기존 Pleos 검증 앱의 readGear를 옮김).
 *
 * - 변경 알림(registerGearSelection)과 주기 재조회(getCurrentGearSelectionState)를 함께 쓴다.
 *   변경 알림은 값이 바뀔 때만 오므로, 재조회가 없으면 P단을 유지해도 값이 오래된 것으로 판정된다.
 * - Vehicle SDK 2.3.2는 Gear API에 "Google GEAR_SELECTION property를 사용하라"는 지원 중단 표시를 달았다.
 *   현재 에뮬레이터에서는 동작을 확인했고, 대체 경로(CarPropertyManager)는 별도 검증 과제로 남긴다.
 *
 * 콜백은 메인 스레드에서 호출된다.
 */
@Suppress("DEPRECATION")
internal class PleosGearSource(
    context: Context,
    private val onReading: (GearReading) -> Unit,
    private val onUnavailable: (String) -> Unit,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var listenerRegistered = false
    private var running = false

    private val listener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            handler.post { if (running) onReading(gear.toReading()) }
        }

        override fun onFailed(e: Exception) {
            handler.post { if (running) onUnavailable("기어 수신 실패: ${e.javaClass.simpleName}") }
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            registerListenerIfNeeded()
            requestCurrentGear()
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    fun start(): Boolean {
        if (running) return true
        return try {
            val sdk = Vehicle(appContext)
            sdk.initialize()
            vehicle = sdk
            running = true
            handler.post(poll)
            true
        } catch (e: Exception) {
            onUnavailable("Vehicle SDK 초기화 실패: ${e.javaClass.simpleName}")
            false
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(poll)
        vehicle?.let { sdk ->
            if (listenerRegistered) runCatching { sdk.getGear().unregisterGearSelection(listener) }
            runCatching { sdk.release() }
        }
        listenerRegistered = false
        vehicle = null
    }

    private fun registerListenerIfNeeded() {
        if (listenerRegistered) return
        val sdk = vehicle ?: return
        listenerRegistered = runCatching { sdk.getGear().registerGearSelection(listener) }.isSuccess
    }

    private fun requestCurrentGear() {
        val sdk = vehicle ?: return
        try {
            sdk.getGear().getCurrentGearSelectionState(
                onComplete = { gear ->
                    handler.post { if (running) onReading(gear.toReading()) }
                },
                onFailed = { error ->
                    handler.post { if (running) onUnavailable("기어 조회 실패: ${error.javaClass.simpleName}") }
                },
            )
        } catch (e: Exception) {
            onUnavailable("기어 호출 실패: ${e.javaClass.simpleName}")
        }
    }

    private fun VehicleGear?.toReading() = GearReading(
        position = toGearPosition(this),
        rawName = this?.name ?: "null",
        source = SignalSource.SDK,
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
    )

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 3_000L

        /** null·GEAR_UNKNOWN은 확인 불가, P만 주차로 본다. */
        fun toGearPosition(gear: VehicleGear?): GearPosition = when (gear) {
            null, VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
            VehicleGear.GEAR_PARK -> GearPosition.PARK
            else -> GearPosition.NOT_PARK
        }
    }
}
