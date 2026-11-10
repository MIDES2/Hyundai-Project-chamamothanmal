package io.chamamothanmal.vehiclestate

/**
 * 신호 출처. 서버 API v0.2의 `source` 값(SDK/MOCK/MANUAL)과 이름을 맞춘다.
 *
 * - SDK: Android 위치 서비스·Pleos SDK 콜백으로 받은 값. 에뮬레이터의 Extended Controls·
 *   Virtual Console 입력도 콜백으로 들어오면 SDK로 기록되므로, 시연 기록에는 실행 환경을 함께 적는다.
 * - MOCK: 앱 코드가 직접 주입한 개발용 값. 디버그 빌드에서만 허용한다.
 * - MANUAL: 사용자가 화면에서 직접 확인한 값(현재 모듈에서는 만들지 않음).
 */
enum class SignalSource { SDK, MOCK, MANUAL }

/** 기어 상태를 재생 판정에 필요한 세 값으로 줄인 것. */
enum class GearPosition { PARK, NOT_PARK, UNKNOWN }

/**
 * @property rawName SDK가 보낸 원래 값 이름(예: GEAR_PARK). 로그·화면 표시용.
 * @property elapsedRealtimeMs 수신 시각. 벽시계가 아니라 SystemClock.elapsedRealtime 기준.
 */
data class GearReading(
    val position: GearPosition,
    val rawName: String,
    val source: SignalSource,
    val elapsedRealtimeMs: Long,
)

/**
 * 좌표는 재생 판정에만 쓰고 서버·로그로 보내지 않는다(docs/security.md).
 *
 * @property accuracyMeters 위치 제공자가 알려준 정확도. 없으면 null.
 * @property elapsedRealtimeMs 위치가 측정된 시각(Location.elapsedRealtimeNanos 기준).
 */
data class LocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val source: SignalSource,
    val elapsedRealtimeMs: Long,
)

/**
 * 한 시점의 차량 상태. 값이 없으면 null이며, 그 이유는 *Error에 남는다.
 */
data class VehicleSnapshot(
    val gear: GearReading? = null,
    val location: LocationReading? = null,
    val gearError: String? = null,
    val locationError: String? = null,
)

/** 메시지 장소. 서버 메시지의 latitude·longitude·radiusMeters(기본 100m)에 대응한다. */
data class Destination(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 100,
)
