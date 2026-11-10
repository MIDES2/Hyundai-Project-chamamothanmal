package io.chamamothanmal.vehiclestate.source

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.chamamothanmal.vehiclestate.NaviEvent
import io.chamamothanmal.vehiclestate.NaviState
import io.chamamothanmal.vehiclestate.SignalSource

/**
 * Pleos NaviHelper로 내비 목적지와 도착·변경·취소 이벤트를 받는다.
 *
 * - 위치(getCurrentLocationInfo)는 쓰지 않는다. Pleos 에뮬레이터에서 기본 내비가 NMEA를 거부해 좌표가 갱신되지 않는다.
 * - 목적지 좌표는 onDestinationInfo(getDestinationInfo 응답)로 받는다. 이름·주소는 저장하지 않는다.
 * - 목적지가 바뀌거나 경로가 취소되면 이전 도착 기록을 지운다.
 *
 * 콜백은 메인 스레드에서 호출된다.
 */
internal class PleosNaviSource(
    context: Context,
    private val onState: (NaviState) -> Unit,
    private val onEvent: (NaviEvent) -> Unit,
    private val onUnavailable: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var naviHelper: NaviHelper? = null
    private var running = false
    private var connected = false

    private var destinationLatitude: Double? = null
    private var destinationLongitude: Double? = null
    private var arrivedAtMs: Long? = null

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val lat = destinationInfo.latitude
            val lon = destinationInfo.longitude
            post {
                if (isValidCoordinate(lat, lon)) {
                    val changed = destinationLatitude != lat || destinationLongitude != lon
                    if (changed) arrivedAtMs = null
                    destinationLatitude = lat
                    destinationLongitude = lon
                    publish()
                    onEvent(NaviEvent.DESTINATION_SET)
                } else {
                    clearDestination()
                    publish()
                }
            }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            post {
                arrivedAtMs = null
                requestDestination()
            }
        }

        override fun onDestinationChanged() {
            post {
                arrivedAtMs = null
                publish()
                onEvent(NaviEvent.DESTINATION_CHANGED)
                requestDestination()
            }
        }

        override fun onRouteCancelled() {
            post {
                clearDestination()
                publish()
                onEvent(NaviEvent.ROUTE_CANCELLED)
            }
        }

        override fun onRouteEnded() {
            // 도착 후에도 올 수 있으므로 목적지·도착 기록은 유지한다.
            post { onEvent(NaviEvent.ROUTE_ENDED) }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            post {
                arrivedAtMs = SystemClock.elapsedRealtime()
                publish()
                onEvent(NaviEvent.ARRIVED)
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            post { onUnavailable("내비 오류: $errorCode") }
        }
    }

    fun start(): Boolean {
        if (running) return true
        return try {
            val sdk = NaviHelper(appContext)
            naviHelper = sdk
            running = true
            sdk.initialize(
                onConnected = {
                    post {
                        runCatching { sdk.addListener(listener) }
                            .onFailure { onUnavailable("내비 리스너 등록 실패: ${it.javaClass.simpleName}"); return@post }
                        connected = true
                        publish()
                        requestDestination()
                    }
                },
                onFailed = { error ->
                    post {
                        connected = false
                        onUnavailable("내비 연결 실패: ${error.javaClass.simpleName}")
                    }
                },
            )
            true
        } catch (e: Exception) {
            running = false
            naviHelper = null
            onUnavailable("내비 SDK 초기화 실패: ${e.javaClass.simpleName}")
            false
        }
    }

    fun stop() {
        running = false
        connected = false
        naviHelper?.let { sdk ->
            runCatching { sdk.removeListener(listener) }
            runCatching { sdk.release() }
        }
        naviHelper = null
        clearDestination()
    }

    /** 현재 목적지를 다시 요청한다. 응답은 onDestinationInfo로 온다. */
    fun requestDestination() {
        val sdk = naviHelper ?: return
        if (!connected) return
        runCatching { sdk.getDestinationInfo() }
            .onFailure { onUnavailable("목적지 요청 실패: ${it.javaClass.simpleName}") }
    }

    private fun clearDestination() {
        destinationLatitude = null
        destinationLongitude = null
        arrivedAtMs = null
    }

    private fun publish() {
        onState(
            NaviState(
                destinationLatitude = destinationLatitude,
                destinationLongitude = destinationLongitude,
                arrivedAtElapsedMs = arrivedAtMs,
                source = SignalSource.SDK,
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            ),
        )
    }

    private fun post(block: () -> Unit) {
        handler.post { if (running) block() }
    }

    companion object {
        /** 경로가 없을 때 0,0이 올 수 있어 목적지 없음으로 본다. */
        internal fun isValidCoordinate(lat: Double, lon: Double): Boolean =
            lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 &&
                !(lat == 0.0 && lon == 0.0)
    }
}
