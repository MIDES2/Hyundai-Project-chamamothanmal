package io.chamamothanmal.vehiclestate.source

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import io.chamamothanmal.vehiclestate.DoorReading
import io.chamamothanmal.vehiclestate.DoorStatus
import io.chamamothanmal.vehiclestate.GearPosition
import io.chamamothanmal.vehiclestate.GearReading
import io.chamamothanmal.vehiclestate.SignalSource

/**
 * Pleos Vehicle SDK 연결 하나로 기어와 도어를 받는다.
 *
 * 기어 (기존 Pleos 검증 앱의 readGear를 옮김)
 * - 변경 알림(registerGearSelection)과 주기 재조회(getCurrentGearSelectionState)를 함께 쓴다.
 *   변경 알림은 값이 바뀔 때만 오므로, 재조회가 없으면 P단을 유지해도 값이 오래된 것으로 판정된다.
 * - Vehicle SDK 2.3.2는 Gear API에 "Google GEAR_SELECTION property를 사용하라"는 지원 중단 표시를 달았다.
 *
 * 도어
 * - checkDoorPositionCapabilities로 지원 도어와 값 범위를 받고, 도어별 변경 알림 + 주기 재조회를 쓴다.
 * - 값이 범위 최솟값보다 크면 열림으로 본다. 하나라도 열리면 OPEN, 모두 닫혔다고 확인돼야 CLOSED.
 *
 * 콜백은 메인 스레드에서 호출된다.
 */
@Suppress("DEPRECATION")
internal class PleosVehicleSource(
    context: Context,
    private val onGear: (GearReading) -> Unit,
    private val onGearUnavailable: (String) -> Unit,
    private val onDoor: (DoorReading) -> Unit,
    private val onDoorUnavailable: (String) -> Unit,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var current: Session? = null

    // SDK reflection/Binder calls must not block input, WebView or keyboard rendering.
    // Each resume owns a separate session; stopped sessions cannot publish late readings.
    fun start(): Boolean {
        if (current != null) return true
        val session = Session()
        current = session
        session.start()
        return true // Initialization completes asynchronously; unknown values remain fail-closed.
    }

    fun stop() {
        val session = current ?: return
        current = null
        session.close()
    }

    private inner class Session {
        private val thread = HandlerThread("PleosVehicleSdk").apply { start() }
        private val sdkHandler = Handler(thread.looper)
        private var vehicle: Vehicle? = null
        private var gearListenerRegistered = false
        private var doorSlots: List<DoorSlot>? = null
        private var doorCapabilityPending = false
        private val active get() = current === this

        private fun emit(action: () -> Unit) { main.post { if (active) action() } }
        private fun dispatch(action: () -> Unit) { sdkHandler.post { if (active) action() } }

        private val gearListener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                val reading = gear.toReading()
                emit { onGear(reading) }
            }
            override fun onFailed(e: Exception) {
                emit { onGearUnavailable("기어 수신 실패: ${e.javaClass.simpleName}") }
            }
        }

        private inner class DoorSlot(val area: DoorArea, val name: String, val closedValue: Int) {
            var value: Int? = null
            var receivedAtMs: Long = 0
            var listener: DoorPositionListener? = null
        }

        private val poll = object : Runnable {
            override fun run() {
                if (!active) return
                registerGearListenerIfNeeded()
                requestCurrentGear()
                if (doorSlots == null) requestDoorCapabilities() else requestCurrentDoors()
                sdkHandler.postDelayed(this, pollIntervalMs)
            }
        }

        fun start() = dispatch {
            try {
                val sdk = Vehicle(appContext)
                vehicle = sdk
                sdk.initialize()
                if (active) sdkHandler.post(poll)
            } catch (e: Exception) {
                val reason = "Vehicle SDK 초기화 실패: ${e.javaClass.simpleName}"
                emit { onGearUnavailable(reason); onDoorUnavailable(reason) }
            }
        }

        fun close() {
            sdkHandler.removeCallbacksAndMessages(null)
            sdkHandler.post {
                vehicle?.let { sdk ->
                    if (gearListenerRegistered) runCatching { sdk.getGear().unregisterGearSelection(gearListener) }
                    doorSlots?.forEach { slot ->
                        slot.listener?.let { listener -> runCatching { sdk.getDoor().unregisterDoorPosition(slot.area, listener) } }
                    }
                    runCatching { sdk.release() }
                }
                vehicle = null
                doorSlots = null
                thread.quitSafely()
            }
        }

        private fun registerGearListenerIfNeeded() {
            if (gearListenerRegistered) return
            val sdk = vehicle ?: return
            gearListenerRegistered = runCatching { sdk.getGear().registerGearSelection(gearListener) }.isSuccess
        }

        private fun requestCurrentGear() {
            val sdk = vehicle ?: return
            try {
                sdk.getGear().getCurrentGearSelectionState(
                    onComplete = { gear -> val reading = gear.toReading(); emit { onGear(reading) } },
                    onFailed = { error -> emit { onGearUnavailable("기어 조회 실패: ${error.javaClass.simpleName}") } },
                )
            } catch (e: Exception) {
                emit { onGearUnavailable("기어 호출 실패: ${e.javaClass.simpleName}") }
            }
        }

        private fun requestDoorCapabilities() {
            if (doorCapabilityPending) return
            val sdk = vehicle ?: return
            doorCapabilityPending = true
            try {
                sdk.getDoor().checkDoorPositionCapabilities(
                    onComplete = { bounds: Map<DoorArea, AreaBounds<Int>> -> dispatch {
                        doorCapabilityPending = false
                        val slots = bounds.map { (area, bound) ->
                            DoorSlot(area, area.values.joinToString("+") { it.name }, bound.minValue ?: 0)
                        }
                        if (slots.isEmpty()) {
                            emit { onDoorUnavailable("지원 도어 없음 (capabilities 비어 있음)") }
                        } else {
                            doorSlots = slots
                            slots.forEach { registerDoorListener(sdk, it) }
                            requestCurrentDoors()
                        }
                    } },
                    onFailed = { error -> dispatch {
                        doorCapabilityPending = false
                        emit { onDoorUnavailable("도어 지원 조회 실패: ${error.javaClass.simpleName}") }
                    } },
                )
            } catch (e: Exception) {
                doorCapabilityPending = false
                emit { onDoorUnavailable("도어 호출 실패: ${e.javaClass.simpleName}") }
            }
        }

        private fun registerDoorListener(sdk: Vehicle, slot: DoorSlot) {
            val listener = object : DoorPositionListener {
                override fun onPositionUpdated(position: Int?) { dispatch { updateDoor(slot, position) } }
                override fun onFailed(e: Exception) { dispatch { updateDoor(slot, null) } }
            }
            if (runCatching { sdk.getDoor().registerDoorPosition(slot.area, listener) }.isSuccess) slot.listener = listener
        }

        private fun requestCurrentDoors() {
            val sdk = vehicle ?: return
            doorSlots?.forEach { slot ->
                try {
                    sdk.getDoor().getCurrentDoorPosition(slot.area,
                        onComplete = { position -> dispatch { updateDoor(slot, position) } },
                        onFailed = { dispatch { updateDoor(slot, null) } },
                    )
                } catch (e: Exception) { updateDoor(slot, null) }
            }
        }

        private fun updateDoor(slot: DoorSlot, position: Int?) {
            slot.value = position
            slot.receivedAtMs = SystemClock.elapsedRealtime()
            val slots = doorSlots ?: return
            val reading = combineDoors(slots.map { DoorValue(it.name, it.value, it.closedValue, it.receivedAtMs) })
            emit { onDoor(reading) }
        }
    }

    private fun VehicleGear?.toReading() = GearReading(
        position = toGearPosition(this), rawName = this?.name ?: "null",
        source = SignalSource.SDK, elapsedRealtimeMs = SystemClock.elapsedRealtime(),
    )

    /** 도어별 값. receivedAtMs가 0이면 아직 받지 못한 것. */
    internal data class DoorValue(val name: String, val position: Int?, val closedValue: Int, val receivedAtMs: Long)

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 3_000L

        /** null·GEAR_UNKNOWN은 확인 불가, P만 주차로 본다. */
        fun toGearPosition(gear: VehicleGear?): GearPosition = when (gear) {
            null, VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
            VehicleGear.GEAR_PARK -> GearPosition.PARK
            else -> GearPosition.NOT_PARK
        }

        /** 하나라도 열리면 OPEN, 모든 도어 값이 있고 닫힘이면 CLOSED, 그 외 UNKNOWN. 시각은 가장 오래된 값 기준. */
        internal fun combineDoors(values: List<DoorValue>): DoorReading {
            val open = values.filter { it.position != null && it.position > it.closedValue }.map { it.name }
            val status = when {
                open.isNotEmpty() -> DoorStatus.OPEN
                values.isNotEmpty() && values.all { it.position != null && it.receivedAtMs > 0 } -> DoorStatus.CLOSED
                else -> DoorStatus.UNKNOWN
            }
            // 한 번도 받지 못한 도어가 있으면 0(미수신)이 가장 오래된 값이 된다.
            val oldest = values.minOfOrNull { it.receivedAtMs } ?: 0L
            return DoorReading(status, open, SignalSource.SDK, oldest)
        }
    }
}
