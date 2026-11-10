package io.chamamothanmal.vehiclestate

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.Manifest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pleos 에뮬레이터에서 실제 콜백 수신을 확인하는 기기 테스트. 실차 검증이 아니다.
 *
 * 선택 인자 (-e):
 * - expectLat, expectLon: 이 좌표 50m 이내로 위치가 바뀌는지 확인 (adb emu geo fix로 바꾼 값)
 * - expectGear: PARK 또는 NOT_PARK. Virtual Console로 바꾼 기어가 반영되는지 확인
 */
@RunWith(AndroidJUnit4::class)
class VehicleStateMonitorDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private lateinit var monitor: VehicleStateMonitor
    private lateinit var host: ActivityScenario<ForegroundHostActivity>

    @Before
    fun setUp() {
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        // 실제 앱처럼 화면이 보이는 상태에서 측정한다(백그라운드에서는 위치가 거부된다).
        host = ActivityScenario.launch(ForegroundHostActivity::class.java)
        instrumentation.runOnMainSync {
            monitor = VehicleStateMonitor(context)
            monitor.start()
        }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { monitor.stop() }
        host.close()
    }

    private fun snapshot(): VehicleSnapshot {
        var result: VehicleSnapshot? = null
        instrumentation.runOnMainSync { result = monitor.snapshot }
        return result!!
    }

    private fun waitFor(timeoutMs: Long, condition: (VehicleSnapshot) -> Boolean): VehicleSnapshot {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var last = snapshot()
        while (SystemClock.elapsedRealtime() < deadline) {
            last = snapshot()
            if (condition(last)) return last
            Thread.sleep(250)
        }
        return last
    }

    @Test
    fun receivesFreshGpsLocation() {
        val s = waitFor(20_000) { snap ->
            val loc = snap.location
            loc != null && SystemClock.elapsedRealtime() - loc.elapsedRealtimeMs < 3_000
        }
        val loc = s.location
        assertNotNull("위치 미수신: ${s.locationError}", loc)
        assertEquals(SignalSource.SDK, loc!!.source)
        Log.i(TAG, "GPS 수신 확인 accuracy=${loc.accuracyMeters}")
    }

    @Test
    fun followsLocationChange() {
        val lat = args.getString("expectLat")?.toDoubleOrNull()
        val lon = args.getString("expectLon")?.toDoubleOrNull()
        assumeNotNull(lat, lon)
        val s = waitFor(30_000) { snap ->
            val loc = snap.location ?: return@waitFor false
            PlaybackGate.distanceMeters(loc.latitude, loc.longitude, lat!!, lon!!) < 50
        }
        val loc = s.location
        assertNotNull("위치 미수신: ${s.locationError}", loc)
        val d = PlaybackGate.distanceMeters(loc!!.latitude, loc.longitude, lat!!, lon!!)
        assertTrue("변경한 위치가 반영되지 않음 (거리 ${d.toInt()}m)", d < 50)
        Log.i(TAG, "위치 변경 반영 확인 (거리 ${d.toInt()}m)")
    }

    @Test
    fun receivesGearFromPleosSdk() {
        val s = waitFor(20_000) { it.gear != null }
        val gear = s.gear
        assertNotNull("기어 미수신: ${s.gearError}", gear)
        assertEquals(SignalSource.SDK, gear!!.source)
        Log.i(TAG, "기어 수신 확인 raw=${gear.rawName} position=${gear.position}")

        val expected = args.getString("expectGear")?.let { GearPosition.valueOf(it) } ?: return
        // Virtual Console을 사람이 조작할 시간을 둔다.
        val changed = waitFor(60_000) { it.gear?.position == expected }
        assertEquals("기어 변경 미반영", expected, changed.gear?.position)
    }

    @Test
    fun gearStaysFreshWhileUnchanged() {
        // 변경 알림이 없어도 주기 재조회로 값이 계속 갱신돼야 한다(P단 유지 중 오래됨 오판 방지).
        waitFor(20_000) { it.gear != null }
        Thread.sleep(12_000)
        val gear = snapshot().gear
        assertNotNull(gear)
        val age = SystemClock.elapsedRealtime() - gear!!.elapsedRealtimeMs
        assertTrue("기어 값이 갱신되지 않음 (${age}ms)", age < GatePolicy().maxGearAgeMs)
    }

    @Test
    fun holdsAfterStop() {
        var decision: PlaybackDecision? = null
        instrumentation.runOnMainSync {
            monitor.stop()
            decision = monitor.evaluate(Destination(37.0, 127.0))
        }
        assertTrue(decision is PlaybackDecision.Hold)
        assertEquals(HoldReason.GEAR_UNKNOWN, (decision as PlaybackDecision.Hold).reason)
    }

    @Test
    fun evaluatesWithRealSignals() {
        // 시작 직후에는 오래된 마지막 위치가 먼저 올 수 있으므로(이때는 LOCATION_STALE로 보류됨) 새 위치를 기다린다.
        val s = waitFor(20_000) { snap ->
            val l = snap.location
            snap.gear != null && l != null && SystemClock.elapsedRealtime() - l.elapsedRealtimeMs < 3_000
        }
        val loc = s.location
        assertNotNull("새 위치 미수신: ${s.locationError}", loc)
        loc!!
        var decision: PlaybackDecision? = null
        instrumentation.runOnMainSync {
            decision = monitor.evaluate(Destination(loc.latitude, loc.longitude), GatePolicy(allowMock = false))
        }
        val door = snapshot().door
        Log.i(TAG, "실제 신호 판정: $decision (gear=${s.gear?.rawName}, door=${door?.status}, naviDest=${s.navi?.hasDestination})")
        when {
            s.gear?.position == GearPosition.NOT_PARK ->
                assertEquals(HoldReason.NOT_PARKED, (decision as PlaybackDecision.Hold).reason)
            s.gear?.position == GearPosition.PARK && door?.status == DoorStatus.CLOSED && s.navi?.hasDestination != true ->
                assertTrue("P단·닫힘·도착인데 보류: $decision", decision is PlaybackDecision.Allow)
            else -> assertTrue(decision is PlaybackDecision.Hold)
        }
    }

    // ── 도어 ──

    /**
     * 도어 수신을 확인한다. -e expectDoor OPEN 이면 60초 안에 Virtual Console에서 도어를 열 때 반영되는지 확인한다.
     * (에뮬레이터는 SDK setDoorPosition 요청을 성공 응답하지만 값이 바뀌지 않아 Virtual Console로만 바꿀 수 있다.)
     */
    @Test
    fun receivesDoorFromPleosSdk() {
        val first = waitFor(20_000) { it.door != null && it.door!!.status != DoorStatus.UNKNOWN }
        assertNotNull("도어 미수신: ${first.doorError}", first.door)
        assertEquals(SignalSource.SDK, first.door!!.source)
        Log.i(TAG, "도어 수신 확인 status=${first.door!!.status}")

        val expected = args.getString("expectDoor")?.let { DoorStatus.valueOf(it) } ?: return
        val changed = waitFor(60_000) { it.door?.status == expected }
        assertEquals("도어 변경 미반영", expected, changed.door?.status)
        if (expected == DoorStatus.OPEN) {
            var decision: PlaybackDecision? = null
            instrumentation.runOnMainSync { decision = monitor.evaluate(Destination(37.0, 127.0)) }
            assertEquals(HoldReason.DOOR_OPEN, (decision as PlaybackDecision.Hold).reason)
            Log.i(TAG, "도어 열림 반영 확인 open=${changed.door?.openDoors} → $decision")
        }
    }

    // ── 내비 ──

    @Test
    fun followsNaviDestinationAndCancel() {
        val events = mutableListOf<NaviEvent>()
        instrumentation.runOnMainSync { monitor.addNaviEventListener { synchronized(events) { events += it } } }

        // 테스트 전용 NAVI_ROUTE_SEARCH 권한으로 경로를 요청한다. 합성 좌표.
        val lat = 37.4979
        val lon = 127.0276
        val navi = NaviHelper(instrumentation.targetContext)
        try {
            awaitValue<Unit>("내비 연결") { ok, fail -> navi.initialize(onConnected = { ok(Unit) }, onFailed = fail) }
            navi.requestRoute(RouteInfo(lat, lon, null, null, "테스트 목적지", "", "", "", RouteOption.RECOMMENDED, "", false))

            val set = waitFor(40_000) { snap ->
                val n = snap.navi
                n != null && n.hasDestination &&
                    PlaybackGate.distanceMeters(n.destinationLatitude!!, n.destinationLongitude!!, lat, lon) < 300
            }
            Log.i(TAG, "내비 목적지 수신 hasDestination=${set.navi?.hasDestination} events=${synchronized(events) { events.toList() }} error=${set.naviError}")
            assertTrue("내비 목적지 미반영: ${set.naviError}", set.navi?.hasDestination == true)

            // 목적지가 메시지 장소와 다르면 불일치로 보류돼야 한다.
            var decision: PlaybackDecision? = null
            instrumentation.runOnMainSync { decision = monitor.evaluate(Destination(37.5665, 126.9780)) }
            Log.i(TAG, "다른 장소 메시지 판정: $decision")

            navi.cancelRoute()
            val cancelled = waitFor(20_000) { it.navi?.hasDestination == false }
            Log.i(TAG, "경로 취소 후 hasDestination=${cancelled.navi?.hasDestination} events=${synchronized(events) { events.toList() }}")
            assertTrue("경로 취소 미반영", cancelled.navi?.hasDestination == false)
        } finally {
            runCatching { navi.cancelRoute() }
            navi.release()
        }
    }

    /**
     * 진단용: 내비 현재 위치가 에뮬레이터 위치 변경을 따라가는지 확인한다(모듈 판정에는 쓰지 않음).
     * -e naviExpectLat/-e naviExpectLon 이 있을 때만 실행. 다른 터미널에서 adb emu geo nmea 로 문장을 보내는 동안 실행.
     */
    @Test
    fun probeNaviCurrentLocation() {
        val lat = args.getString("naviExpectLat")?.toDoubleOrNull()
        val lon = args.getString("naviExpectLon")?.toDoubleOrNull()
        assumeNotNull(lat, lon)
        val navi = NaviHelper(instrumentation.targetContext)
        val latest = java.util.concurrent.atomic.AtomicReference<Pair<Double, Double>?>(null)
        val listener = object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                latest.set(currentLocationInfo.latitude to currentLocationInfo.longitude)
            }
        }
        try {
            awaitValue<Unit>("내비 연결") { ok, fail -> navi.initialize(onConnected = { ok(Unit) }, onFailed = fail) }
            navi.addListener(listener)
            var distance = Double.MAX_VALUE
            val deadline = SystemClock.elapsedRealtime() + 40_000
            while (SystemClock.elapsedRealtime() < deadline) {
                navi.getCurrentLocationInfo()
                Thread.sleep(2_000)
                latest.get()?.let { (a, b) -> distance = PlaybackGate.distanceMeters(a, b, lat!!, lon!!) }
                if (distance < 200) break
            }
            Log.i(TAG, "내비 현재 위치와 기대 위치 거리 ${if (distance == Double.MAX_VALUE) "응답 없음" else "${distance.toInt()}m"}")
            assertTrue("내비 위치가 기대 위치로 바뀌지 않음", distance < 200)
        } finally {
            runCatching { navi.removeListener(listener) }
            navi.release()
        }
    }

    /** 콜백 API를 동기적으로 기다린다. */
    private fun <T> awaitValue(label: String, call: (ok: (T) -> Unit, fail: (Throwable) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicReference<Result<T>>()
        call({ result.set(Result.success(it)); latch.countDown() }, { result.set(Result.failure(it)); latch.countDown() })
        assertTrue("$label 응답 없음", latch.await(20, TimeUnit.SECONDS))
        return result.get().getOrElse { throw AssertionError("$label 실패: ${it.javaClass.simpleName}: ${it.message}", it) }
    }

    private companion object {
        const val TAG = "VehicleStateTest"
    }
}
