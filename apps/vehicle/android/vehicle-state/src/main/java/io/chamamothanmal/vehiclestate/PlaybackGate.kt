package io.chamamothanmal.vehiclestate

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 재생 판정 기준값.
 *
 * @property maxGearAgeMs 기어 값이 이보다 오래되면 미확인으로 본다. VehicleStateMonitor는 기어를
 *   3초마다 다시 조회하므로 기본 10초면 두 번 연속 실패할 때 보류된다.
 * @property maxLocationAgeMs 위치 값이 이보다 오래되면 미확인으로 본다. GPS는 1초 간격으로 받는다.
 * @property maxLocationAccuracyMeters 정확도가 이보다 나쁘면 위치 미확인으로 본다.
 * @property allowMock MOCK 출처 값을 판정에 쓸지 여부. 릴리스 빌드에서는 false를 유지한다.
 */
data class GatePolicy(
    val maxGearAgeMs: Long = 10_000,
    val maxLocationAgeMs: Long = 15_000,
    val maxLocationAccuracyMeters: Float = 100f,
    val allowMock: Boolean = false,
)

/**
 * 보류 사유. [contractReason]은 서버 API v0.2 재생 이벤트의 reason 값이다.
 * null이면 서버에 HELD 이벤트를 보내지 않고 PENDING으로 둔다(아직 도착 전).
 */
enum class HoldReason(val contractReason: String?) {
    GEAR_UNKNOWN("STATE_UNKNOWN"),
    GEAR_STALE("STATE_UNKNOWN"),
    NOT_PARKED("DRIVING"),
    LOCATION_UNKNOWN("STATE_UNKNOWN"),
    LOCATION_STALE("STATE_UNKNOWN"),
    LOCATION_INACCURATE("STATE_UNKNOWN"),
    MOCK_NOT_ALLOWED("STATE_UNKNOWN"),
    NOT_ARRIVED(null),
}

sealed class PlaybackDecision {
    /**
     * 차량 상태 조건 충족. 문 닫힘·오디오 포커스·서버 유효성 확인은 별도로 해야 한다.
     * @property source 하나라도 MOCK이면 MOCK. 서버 세션·이벤트의 source로 그대로 보낸다.
     */
    data class Allow(val source: SignalSource, val distanceMeters: Double) : PlaybackDecision()

    data class Hold(val reason: HoldReason, val detail: String) : PlaybackDecision()
}

/**
 * 기어·위치 상태로 재생 허용 여부를 정한다. Android에 의존하지 않는 순수 함수라 JVM 단위 테스트로 검증한다.
 *
 * 원칙: 값이 없거나, 오래됐거나, 시각이 이상하면 허용하지 않는다(AGENTS.md "미확인 상태를 재생 허용으로 처리하지 않는다").
 */
object PlaybackGate {

    fun evaluate(
        snapshot: VehicleSnapshot,
        destination: Destination,
        nowElapsedRealtimeMs: Long,
        policy: GatePolicy = GatePolicy(),
    ): PlaybackDecision {
        val gear = snapshot.gear
            ?: return hold(HoldReason.GEAR_UNKNOWN, snapshot.gearError ?: "기어 값을 아직 받지 못함")

        val gearAge = nowElapsedRealtimeMs - gear.elapsedRealtimeMs
        if (gearAge < 0 || gearAge > policy.maxGearAgeMs) {
            return hold(HoldReason.GEAR_STALE, "기어 값이 오래됨 (${gearAge}ms)")
        }
        when (gear.position) {
            GearPosition.UNKNOWN -> return hold(HoldReason.GEAR_UNKNOWN, "SDK 응답이 확인 불가: ${gear.rawName}")
            GearPosition.NOT_PARK -> return hold(HoldReason.NOT_PARKED, "P단 아님: ${gear.rawName}")
            GearPosition.PARK -> Unit
        }

        val location = snapshot.location
            ?: return hold(HoldReason.LOCATION_UNKNOWN, snapshot.locationError ?: "위치 값을 아직 받지 못함")

        val locationAge = nowElapsedRealtimeMs - location.elapsedRealtimeMs
        if (locationAge < 0 || locationAge > policy.maxLocationAgeMs) {
            return hold(HoldReason.LOCATION_STALE, "위치 값이 오래됨 (${locationAge}ms)")
        }
        val accuracy = location.accuracyMeters
        if (accuracy != null && (accuracy.isNaN() || accuracy > policy.maxLocationAccuracyMeters)) {
            return hold(HoldReason.LOCATION_INACCURATE, "위치 정확도 부족 (${accuracy}m)")
        }
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) {
            return hold(HoldReason.LOCATION_UNKNOWN, "위치 좌표가 유효하지 않음")
        }

        val usesMock = gear.source == SignalSource.MOCK || location.source == SignalSource.MOCK
        if (usesMock && !policy.allowMock) {
            return hold(HoldReason.MOCK_NOT_ALLOWED, "MOCK 값은 이 빌드에서 허용되지 않음")
        }

        val distance = distanceMeters(
            location.latitude, location.longitude,
            destination.latitude, destination.longitude,
        )
        if (distance > destination.radiusMeters) {
            return hold(HoldReason.NOT_ARRIVED, "목적지 반경 밖")
        }

        val source = when {
            usesMock -> SignalSource.MOCK
            gear.source == SignalSource.MANUAL || location.source == SignalSource.MANUAL -> SignalSource.MANUAL
            else -> SignalSource.SDK
        }
        return PlaybackDecision.Allow(source, distance)
    }

    /** 두 좌표 사이 거리(m). 하버사인 공식. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    private fun hold(reason: HoldReason, detail: String) = PlaybackDecision.Hold(reason, detail)

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
