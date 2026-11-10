package io.chamamothanmal.vehiclestate

import android.Manifest
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
        Log.i(TAG, "실제 신호 판정: $decision (gear=${s.gear?.rawName})")
        when (s.gear?.position) {
            GearPosition.PARK -> assertTrue("P단·도착인데 보류: $decision", decision is PlaybackDecision.Allow)
            GearPosition.NOT_PARK -> assertEquals(HoldReason.NOT_PARKED, (decision as PlaybackDecision.Hold).reason)
            else -> assertTrue(decision is PlaybackDecision.Hold)
        }
    }

    private companion object {
        const val TAG = "VehicleStateTest"
    }
}
