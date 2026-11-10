package io.chamamothanmal.vehiclestate

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.os.SystemClock
import io.chamamothanmal.vehiclestate.source.AndroidGpsLocationSource
import io.chamamothanmal.vehiclestate.source.PleosNaviSource
import io.chamamothanmal.vehiclestate.source.PleosVehicleSource

/**
 * 차량 앱이 쓰는 차량 상태 진입점.
 *
 * | 신호 | 출처 |
 * | --- | --- |
 * | 기어(P단) | Pleos Vehicle SDK Gear |
 * | 도어 | Pleos Vehicle SDK Door |
 * | 위치 | Android LocationManager GPS |
 * | 내비 목적지·도착·변경·취소 | Pleos NaviHelper 이벤트 |
 *
 * 사용 순서 (모든 메서드는 메인 스레드에서 호출):
 * 1. Activity에서 [REQUIRED_RUNTIME_PERMISSIONS]를 요청한다(모듈은 권한을 요청하지 않는다).
 * 2. [start] — 권한이 허용된 뒤 다시 호출해도 된다.
 * 3. 재생 직전 [evaluate]. Allow가 아니면 재생하지 않는다. 재생 중에는 [addListener]로 상태 변화를 받아 다시 판정한다.
 * 4. [addNaviEventListener]로 목적지 변경·경로 취소를 받아 서버 세션을 종료한다.
 * 5. [stop] — onDestroy 등에서 호출.
 *
 * 실제 신호와 MOCK은 각 값의 source로 구분되며, MOCK 주입(setMock*)은 디버그 빌드에서만 받는다.
 */
class VehicleStateMonitor(context: Context) {

    fun interface Listener {
        fun onSnapshotChanged(snapshot: VehicleSnapshot)
    }

    fun interface NaviEventListener {
        fun onNaviEvent(event: NaviEvent)
    }

    private val appContext = context.applicationContext
    private val listeners = mutableSetOf<Listener>()
    private val naviEventListeners = mutableSetOf<NaviEventListener>()

    private var realGear: GearReading? = null
    private var realLocation: LocationReading? = null
    private var realDoor: DoorReading? = null
    private var realNavi: NaviState? = null
    private var gearError: String? = NOT_STARTED
    private var locationError: String? = NOT_STARTED
    private var doorError: String? = NOT_STARTED
    private var naviError: String? = NOT_STARTED

    private var mockGear: GearPosition? = null
    private var mockLocation: Pair<Double, Double>? = null
    private var mockDoor: DoorStatus? = null
    private var mockNavi: Triple<Double, Double, Boolean>? = null

    private val vehicleSource = PleosVehicleSource(
        appContext,
        onGear = { realGear = it; gearError = null; notifyListeners() },
        // 조회 실패 시 이전 값을 지운다. 오래된 P단·닫힘 값으로 재생되지 않게 한다.
        onGearUnavailable = { realGear = null; gearError = it; notifyListeners() },
        onDoor = { realDoor = it; doorError = null; notifyListeners() },
        onDoorUnavailable = { realDoor = null; doorError = it; notifyListeners() },
    )

    private val locationSource = AndroidGpsLocationSource(
        appContext,
        onReading = { realLocation = it; locationError = null; notifyListeners() },
        onUnavailable = { realLocation = null; locationError = it; notifyListeners() },
    )

    private val naviSource = PleosNaviSource(
        appContext,
        onState = { realNavi = it; naviError = null; notifyListeners() },
        onEvent = { event -> naviEventListeners.toList().forEach { it.onNaviEvent(event) } },
        // 내비 오류는 목적지 정보를 지우지 않고 기록만 한다(도착 판정은 GPS가 우선).
        onUnavailable = { naviError = it; notifyListeners() },
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
            val door = mockDoor?.let {
                DoorReading(it, if (it == DoorStatus.OPEN) listOf("MOCK") else emptyList(), SignalSource.MOCK, now)
            } ?: realDoor
            val navi = mockNavi?.let { (lat, lon, arrived) ->
                NaviState(lat, lon, if (arrived) now else null, SignalSource.MOCK, now)
            } ?: realNavi
            return VehicleSnapshot(
                gear = gear,
                location = location,
                door = door,
                navi = navi,
                gearError = if (gear == null) gearError else null,
                locationError = if (location == null) locationError else null,
                doorError = if (door == null) doorError else null,
                naviError = naviError,
            )
        }

    fun hasLocationPermission(): Boolean = locationSource.hasPermission()

    /** 기어·도어·위치·내비 수신을 시작한다. 이미 시작된 항목은 건너뛴다. */
    fun start() {
        checkMainThread()
        if (vehicleSource.start()) {
            if (realGear == null && gearError == NOT_STARTED) gearError = WAITING
            if (realDoor == null && doorError == NOT_STARTED) doorError = WAITING
        }
        if (locationSource.start() && realLocation == null && locationError == NOT_STARTED) locationError = WAITING
        if (naviSource.start() && naviError == NOT_STARTED) naviError = WAITING
        notifyListeners()
    }

    fun stop() {
        checkMainThread()
        vehicleSource.stop()
        locationSource.stop()
        naviSource.stop()
        realGear = null
        realLocation = null
        realDoor = null
        realNavi = null
        gearError = NOT_STARTED
        locationError = NOT_STARTED
        doorError = NOT_STARTED
        naviError = NOT_STARTED
        notifyListeners()
    }

    /** 재생 직전에 호출한다. */
    fun evaluate(
        destination: Destination,
        policy: GatePolicy = GatePolicy(allowMock = isDebugBuild),
    ): PlaybackDecision =
        PlaybackGate.evaluate(snapshot, destination, SystemClock.elapsedRealtime(), policy)

    /** 내비 목적지를 다시 요청한다(출발 전 "이번 이동에서 듣기" 화면 진입 시 등). */
    fun refreshNaviDestination() {
        checkMainThread()
        naviSource.requestDestination()
    }

    // ── MOCK (디버그 빌드 전용, null이면 해제하고 실제 값으로 돌아감) ──

    fun setMockGear(position: GearPosition?): Boolean = setMock(position != null) { mockGear = position }

    fun setMockLocation(latitude: Double?, longitude: Double?): Boolean {
        val pair = if (latitude != null && longitude != null) latitude to longitude else null
        return setMock(pair != null) { mockLocation = pair }
    }

    fun setMockDoor(status: DoorStatus?): Boolean = setMock(status != null) { mockDoor = status }

    fun setMockNavi(latitude: Double?, longitude: Double?, arrived: Boolean = false): Boolean {
        val value = if (latitude != null && longitude != null) Triple(latitude, longitude, arrived) else null
        return setMock(value != null) { mockNavi = value }
    }

    private fun setMock(enabling: Boolean, apply: () -> Unit): Boolean {
        checkMainThread()
        if (enabling && !isDebugBuild) return false
        apply()
        notifyListeners()
        return true
    }

    // ── 리스너 ──

    fun addListener(listener: Listener) {
        checkMainThread()
        listeners += listener
        listener.onSnapshotChanged(snapshot)
    }

    fun removeListener(listener: Listener) {
        checkMainThread()
        listeners -= listener
    }

    fun addNaviEventListener(listener: NaviEventListener) {
        checkMainThread()
        naviEventListeners += listener
    }

    fun removeNaviEventListener(listener: NaviEventListener) {
        checkMainThread()
        naviEventListeners -= listener
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

        /**
         * Activity에서 요청할 실행 중 권한.
         * pleos.car.permission.CAR_DRIVE·CAR_DOORS·NAVI_ROUTE는 모듈 Manifest 선언만 한다.
         */
        val REQUIRED_RUNTIME_PERMISSIONS = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
