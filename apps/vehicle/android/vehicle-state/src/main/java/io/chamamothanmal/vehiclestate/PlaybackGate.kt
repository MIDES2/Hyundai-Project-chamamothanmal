package io.chamamothanmal.vehiclestate

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 재생 판정 기준값.
 *
 * @property maxGearAgeMs 기어 값이 이보다 오래되면 미확인으로 본다. 기어는 3초마다 다시 조회한다.
 * @property maxDoorAgeMs 도어 값이 이보다 오래되면 미확인으로 본다. 도어도 3초마다 다시 조회한다.
 * @property maxLocationAgeMs 위치 값이 이보다 오래되면 미확인으로 본다. GPS는 1초 간격으로 받는다.
 * @property maxLocationAccuracyMeters 정확도가 이보다 나쁘면 위치 미확인으로 본다.
 * @property maxNaviArrivalAgeMs 내비 도착 이벤트를 도착 근거로 인정하는 시간.
 * @property requireDoorClosed 도어 닫힘 확인을 요구할지 여부. 기획서 기준 true.
 * @property allowMock MOCK 출처 값을 판정에 쓸지 여부. 릴리스 빌드에서는 false를 유지한다.
 */
data class GatePolicy(
    val maxGearAgeMs: Long = 10_000,
    val maxDoorAgeMs: Long = 10_000,
    val maxLocationAgeMs: Long = 15_000,
    val maxLocationAccuracyMeters: Float = 100f,
    val maxNaviArrivalAgeMs: Long = 30 * 60_000L,
    val requireDoorClosed: Boolean = true,
    val allowMock: Boolean = false,
)

/**
 * 보류 사유. [contractReason]은 서버 API v0.2 재생 이벤트의 reason 값이다.
 * null이면 서버에 HELD 이벤트를 보내지 않는다(도착 전은 PENDING 유지, 목적지 불일치는 세션 종료로 처리).
 */
enum class HoldReason(val contractReason: String?) {
    GEAR_UNKNOWN("STATE_UNKNOWN"),
    GEAR_STALE("STATE_UNKNOWN"),
    NOT_PARKED("DRIVING"),
    DOOR_UNKNOWN("STATE_UNKNOWN"),
    DOOR_STALE("STATE_UNKNOWN"),
    DOOR_OPEN("DOOR_OPEN"),
    DESTINATION_MISMATCH(null),
    LOCATION_UNKNOWN("STATE_UNKNOWN"),
    LOCATION_STALE("STATE_UNKNOWN"),
    LOCATION_INACCURATE("STATE_UNKNOWN"),
    MOCK_NOT_ALLOWED("STATE_UNKNOWN"),
    NOT_ARRIVED(null),
}

/** 도착을 무엇으로 확인했는지. */
enum class ArrivalEvidence { GPS, NAVI }

sealed class PlaybackDecision {
    /**
     * 차량 상태 조건 충족. 오디오 포커스·서버 세션/메시지 유효성 확인은 별도로 해야 한다.
     * @property source 판정에 쓴 값 중 하나라도 MOCK이면 MOCK. 서버 세션·이벤트의 source로 그대로 보낸다.
     * @property distanceMeters GPS로 확인했을 때 메시지 장소까지 거리. 내비 도착으로 확인했으면 null.
     */
    data class Allow(
        val source: SignalSource,
        val arrival: ArrivalEvidence,
        val distanceMeters: Double?,
    ) : PlaybackDecision()

    data class Hold(val reason: HoldReason, val detail: String) : PlaybackDecision()
}

/**
 * 차량 상태로 재생 허용 여부를 정한다. Android에 의존하지 않는 순수 함수라 JVM 단위 테스트로 검증한다.
 *
 * 원칙: 값이 없거나, 오래됐거나, 시각이 이상하면 허용하지 않는다(AGENTS.md "미확인 상태를 재생 허용으로 처리하지 않는다").
 *
 * 판정 순서: 기어(P단) → 도어(닫힘) → 내비 목적지와 메시지 장소 일치 → 도착(GPS 우선, GPS 미확인 시 내비 도착 이벤트).
 * 새 GPS 값이 반경 밖이면 내비 도착 이벤트가 있어도 도착으로 보지 않는다(신호가 엇갈리면 보류).
 */
object PlaybackGate {

    fun evaluate(
        snapshot: VehicleSnapshot,
        destination: Destination,
        nowElapsedRealtimeMs: Long,
        policy: GatePolicy = GatePolicy(),
    ): PlaybackDecision {
        val sources = mutableListOf<SignalSource>()

        // 1. 기어
        val gear = snapshot.gear
            ?: return hold(HoldReason.GEAR_UNKNOWN, snapshot.gearError ?: "기어 값을 아직 받지 못함")
        if (!isFresh(gear.elapsedRealtimeMs, nowElapsedRealtimeMs, policy.maxGearAgeMs)) {
            return hold(HoldReason.GEAR_STALE, "기어 값이 오래됨 (${nowElapsedRealtimeMs - gear.elapsedRealtimeMs}ms)")
        }
        when (gear.position) {
            GearPosition.UNKNOWN -> return hold(HoldReason.GEAR_UNKNOWN, "SDK 응답이 확인 불가: ${gear.rawName}")
            GearPosition.NOT_PARK -> return hold(HoldReason.NOT_PARKED, "P단 아님: ${gear.rawName}")
            GearPosition.PARK -> sources += gear.source
        }

        // 2. 도어
        if (policy.requireDoorClosed) {
            val door = snapshot.door
                ?: return hold(HoldReason.DOOR_UNKNOWN, snapshot.doorError ?: "도어 값을 아직 받지 못함")
            // 열림은 오래된 값이어도 보류 사유로 먼저 알린다.
            if (door.status == DoorStatus.OPEN) {
                return hold(HoldReason.DOOR_OPEN, "도어 열림: ${door.openDoors.joinToString()}")
            }
            if (!isFresh(door.elapsedRealtimeMs, nowElapsedRealtimeMs, policy.maxDoorAgeMs)) {
                return hold(HoldReason.DOOR_STALE, "도어 값이 오래됨 (${nowElapsedRealtimeMs - door.elapsedRealtimeMs}ms)")
            }
            if (door.status == DoorStatus.UNKNOWN) {
                return hold(HoldReason.DOOR_UNKNOWN, "일부 도어 상태 확인 불가")
            }
            sources += door.source
        }

        // 3. 내비 목적지가 설정돼 있으면 메시지 장소와 맞아야 한다.
        val navi = snapshot.navi
        val naviDestinationMatches = navi != null && navi.hasDestination && run {
            val d = distanceMeters(
                navi.destinationLatitude!!, navi.destinationLongitude!!,
                destination.latitude, destination.longitude,
            )
            if (d > destination.radiusMeters) {
                return hold(HoldReason.DESTINATION_MISMATCH, "내비 목적지가 메시지 장소와 다름")
            }
            true
        }

        // 4. 도착
        val location = snapshot.location
        val locationProblem: PlaybackDecision.Hold? = when {
            location == null ->
                hold(HoldReason.LOCATION_UNKNOWN, snapshot.locationError ?: "위치 값을 아직 받지 못함")
            !isFresh(location.elapsedRealtimeMs, nowElapsedRealtimeMs, policy.maxLocationAgeMs) ->
                hold(HoldReason.LOCATION_STALE, "위치 값이 오래됨 (${nowElapsedRealtimeMs - location.elapsedRealtimeMs}ms)")
            location.accuracyMeters != null &&
                (location.accuracyMeters.isNaN() || location.accuracyMeters > policy.maxLocationAccuracyMeters) ->
                hold(HoldReason.LOCATION_INACCURATE, "위치 정확도 부족 (${location.accuracyMeters}m)")
            !location.latitude.isFinite() || !location.longitude.isFinite() ->
                hold(HoldReason.LOCATION_UNKNOWN, "위치 좌표가 유효하지 않음")
            else -> null
        }

        val arrival: ArrivalEvidence
        val distance: Double?
        if (locationProblem == null) {
            location!!
            distance = distanceMeters(
                location.latitude, location.longitude,
                destination.latitude, destination.longitude,
            )
            if (distance > destination.radiusMeters) {
                return hold(HoldReason.NOT_ARRIVED, "목적지 반경 밖")
            }
            arrival = ArrivalEvidence.GPS
            sources += location.source
        } else {
            val arrivedAt = navi?.arrivedAtElapsedMs
            val naviArrived = naviDestinationMatches && arrivedAt != null &&
                isFresh(arrivedAt, nowElapsedRealtimeMs, policy.maxNaviArrivalAgeMs)
            if (!naviArrived) return locationProblem
            arrival = ArrivalEvidence.NAVI
            distance = null
            sources += navi!!.source
        }

        // 5. 출처
        val usesMock = SignalSource.MOCK in sources
        if (usesMock && !policy.allowMock) {
            return hold(HoldReason.MOCK_NOT_ALLOWED, "MOCK 값은 이 빌드에서 허용되지 않음")
        }
        val source = when {
            usesMock -> SignalSource.MOCK
            SignalSource.MANUAL in sources -> SignalSource.MANUAL
            else -> SignalSource.SDK
        }
        return PlaybackDecision.Allow(source, arrival, distance)
    }

    /** 두 좌표 사이 거리(m). 하버사인 공식. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    private fun isFresh(atMs: Long, nowMs: Long, maxAgeMs: Long): Boolean {
        val age = nowMs - atMs
        return age in 0..maxAgeMs
    }

    private fun hold(reason: HoldReason, detail: String) = PlaybackDecision.Hold(reason, detail)

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
