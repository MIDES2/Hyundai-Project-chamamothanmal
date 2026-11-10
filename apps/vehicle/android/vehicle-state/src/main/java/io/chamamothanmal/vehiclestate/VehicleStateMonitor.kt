package io.chamamothanmal.vehiclestate

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.os.SystemClock
import io.chamamothanmal.vehiclestate.source.AndroidGpsLocationSource
import io.chamamothanmal.vehiclestate.source.PleosGearSource

/**
 * 차량 앱이 쓰는 차량 상태 진입점. 기어(Pleos Vehicle SDK)와 위치(Android GPS)를 받아 [snapshot]을 유지하고,
 * [evaluate]로 재생 허용 여부를 판정한다.
 *
 * 사용 순서 (모든 메서드는 메인 스레드에서 호출):
 * 1. Activity에서 [REQUIRED_RUNTIME_PERMISSIONS]를 요청한다(모듈은 권한을 요청하지 않는다).
 * 2. [start] — 권한이 허용된 뒤 다시 호출해도 된다.
 * 3. 재생 직전 [evaluate]. Allow가 아니면 재생하지 않는다.
 * 4. [stop] — onDestroy 등에서 호출.
 *
 * 실제 신호와 MOCK은 [GearReading.source]·[LocationReading.source]로 구분되며,
 * MOCK 주입([setMockGear]·[setMockLocation])은 디버그 빌드에서만 받는다.
 */
class VehicleStateMonitor(context: Context) {

    fun interface Listener {
        fun onSnapshotChanged(snapshot: VehicleSnapshot)
    }

    private val appContext = context.applicationContext
    private val listeners = mutableSetOf<Listener>()

    private var realGear: GearReading? = null
    private var realLocation: LocationReading? = null
    private var gearError: String? = NOT_STARTED
    private var locationError: String? = NOT_STARTED

    private var mockGear: GearPosition? = null
    private var mockLocation: Pair<Double, Double>? = null

    private val gearSource = PleosGearSource(
        appContext,
        onReading = { reading ->
            realGear = reading
            gearError = null
            notifyListeners()
        },
        onUnavailable = { reason ->
            // 조회 실패 시 이전 값을 지운다. 오래된 P단 값으로 재생되지 않게 한다.
            realGear = null
            gearError = reason
            notifyListeners()
        },
    )

    private val locationSource = AndroidGpsLocationSource(
        appContext,
        onReading = { reading ->
            realLocation = reading
            locationError = null
            notifyListeners()
        },
        onUnavailable = { reason ->
            realLocation = null
            locationError = reason
            notifyListeners()
        },
    )

    /** 앱이 debuggable일 때만 true. MOCK 주입과 [GatePolicy.allowMock]의 기본값에 쓴다. */
    val isDebugBuild: Boolean =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** 현재 상태. MOCK이 설정된 항목은 MOCK 값(현재 시각 기준)으로 대체된다. */
    val snapshot: VehicleSnapshot
        get() {
            val now = SystemClock.elapsedRealtime()
            val gear = mockGear?.let { GearReading(it, "MOCK_${it.name}", SignalSource.MOCK, now) } ?: realGear
            val location = mockLocation?.let { (lat, lon) ->
                LocationReading(lat, lon, accuracyMeters = null, source = SignalSource.MOCK, elapsedRealtimeMs = now)
            } ?: realLocation
            return VehicleSnapshot(
                gear = gear,
                location = location,
                gearError = if (gear == null) gearError else null,
                locationError = if (location == null) locationError else null,
            )
        }

    fun hasLocationPermission(): Boolean = locationSource.hasPermission()

    /** 기어·위치 수신을 시작한다. 이미 시작된 항목은 건너뛴다. */
    fun start() {
        checkMainThread()
        if (gearSource.start() && realGear == null && gearError == NOT_STARTED) gearError = WAITING
        if (locationSource.start() && realLocation == null && locationError == NOT_STARTED) locationError = WAITING
        notifyListeners()
    }

    fun stop() {
        checkMainThread()
        gearSource.stop()
        locationSource.stop()
        realGear = null
        realLocation = null
        gearError = NOT_STARTED
        locationError = NOT_STARTED
        notifyListeners()
    }

    /** 재생 직전에 호출한다. */
    fun evaluate(
        destination: Destination,
        policy: GatePolicy = GatePolicy(allowMock = isDebugBuild),
    ): PlaybackDecision =
        PlaybackGate.evaluate(snapshot, destination, SystemClock.elapsedRealtime(), policy)

    /** 디버그 빌드에서 기어를 MOCK 값으로 고정한다. null이면 해제하고 실제 값으로 돌아간다. */
    fun setMockGear(position: GearPosition?): Boolean {
        checkMainThread()
        if (position != null && !isDebugBuild) return false
        mockGear = position
        notifyListeners()
        return true
    }

    /** 디버그 빌드에서 위치를 MOCK 값으로 고정한다. null이면 해제한다. */
    fun setMockLocation(latitude: Double?, longitude: Double?): Boolean {
        checkMainThread()
        if (latitude == null || longitude == null) {
            mockLocation = null
        } else {
            if (!isDebugBuild) return false
            mockLocation = latitude to longitude
        }
        notifyListeners()
        return true
    }

    fun addListener(listener: Listener) {
        checkMainThread()
        listeners += listener
        listener.onSnapshotChanged(snapshot)
    }

    fun removeListener(listener: Listener) {
        checkMainThread()
        listeners -= listener
    }

    private fun notifyListeners() {
        if (listeners.isEmpty()) return
        val current = snapshot
        listeners.toList().forEach { it.onSnapshotChanged(current) }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "VehicleStateMonitor는 메인 스레드에서 호출해야 합니다." }
    }

    companion object {
        private const val NOT_STARTED = "시작 전 또는 중지됨"
        private const val WAITING = "수신 대기 중"

        /** Activity에서 요청할 실행 중 권한. pleos.car.permission.CAR_DRIVE는 Manifest 선언만 한다. */
        val REQUIRED_RUNTIME_PERMISSIONS = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
