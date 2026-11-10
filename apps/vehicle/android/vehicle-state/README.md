# :vehicle-state — 차량 상태와 재생 판정 (재민)

차량 앱(`:app`)이 재생 직전에 "지금 재생해도 되는가"를 묻는 Android Library 모듈입니다.
기어는 Pleos Vehicle SDK, 위치는 Android 표준 GPS에서 받고, 값이 없거나 오래됐으면 재생을 허용하지 않습니다.

> 이 모듈의 결과는 **차량 상태 조건**만 판단합니다. 문 닫힘, 오디오 포커스, 서버 세션·메시지 유효성 확인은 별도로 해야 합니다.
> 에뮬레이터 결과를 실차 검증으로 표시하지 않습니다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `VehicleStateMonitor` | 진입점. 기어·위치 수신 시작/중지, 현재 `snapshot`, `evaluate(destination)` |
| `PlaybackGate` | Android 비의존 순수 판정 함수. JVM 단위 테스트 대상 |
| `VehicleSignals.kt` | `SignalSource`(SDK/MOCK/MANUAL), `GearReading`, `LocationReading`, `VehicleSnapshot`, `Destination` |
| `source/PleosGearSource` | `Vehicle.getGear()` 변경 알림 + 3초 주기 재조회 |
| `source/AndroidGpsLocationSource` | `LocationManager` GPS 1초 간격 수신 |

## 판정 규칙 (`PlaybackGate`)

위에서부터 순서대로 확인하고 처음 걸리는 사유로 보류합니다.

| 순서 | 조건 | 결과 `HoldReason` | 서버 이벤트 reason |
| --- | --- | --- | --- |
| 1 | 기어 값 없음 / SDK가 GEAR_UNKNOWN·null | `GEAR_UNKNOWN` | `STATE_UNKNOWN` |
| 2 | 기어 값이 10초보다 오래됨 또는 시각이 미래 | `GEAR_STALE` | `STATE_UNKNOWN` |
| 3 | P단 아님 | `NOT_PARKED` | `DRIVING` |
| 4 | 위치 없음 / 좌표 비정상 | `LOCATION_UNKNOWN` | `STATE_UNKNOWN` |
| 5 | 위치가 15초보다 오래됨 또는 시각이 미래 | `LOCATION_STALE` | `STATE_UNKNOWN` |
| 6 | 정확도 100m 초과 | `LOCATION_INACCURATE` | `STATE_UNKNOWN` |
| 7 | MOCK 값 사용 + `allowMock=false` | `MOCK_NOT_ALLOWED` | `STATE_UNKNOWN` |
| 8 | 메시지 장소 반경(`radiusMeters`, 기본 100m) 밖 | `NOT_ARRIVED` | 없음 (PENDING 유지, 이벤트 보내지 않음) |
| — | 모두 통과 | `Allow(source)` | MOCK이 섞이면 `source=MOCK` |

- 기준값은 `GatePolicy`로 바꿀 수 있습니다. 시각은 `SystemClock.elapsedRealtime` 기준이라 기기 시계 변경에 영향받지 않습니다.
- 기어·위치 조회가 실패하면 이전 값을 즉시 지웁니다. 오래된 P단 값으로 재생되지 않습니다.
- `evaluate()`의 기본 `allowMock`은 앱이 debuggable일 때만 true입니다. MOCK 주입(`setMockGear`, `setMockLocation`)도 디버그 빌드에서만 받습니다.

## 규혁님 앱에 연결할 지점 (아직 연결하지 않음)

`:app`은 이 모듈에 아직 의존하지 않습니다. 연결할 때 바꿀 곳은 아래와 같습니다. 줄 번호는 `app/src/main/java/com/example/malttuk/MainActivity.kt` 현재 버전 기준입니다.

| # | 위치 | 현재 | 연결 방법 |
| --- | --- | --- | --- |
| 0 | `app/build.gradle.kts` dependencies | — | `implementation(project(":vehicle-state"))` |
| 1 | `onCreate` (47행) | 오디오 설정만 | `VehicleStateMonitor(this)` 생성, `REQUIRED_RUNTIME_PERMISSIONS` 요청 후 `start()` |
| 2 | `startTestPlayback()` 첫 조건 (264행) | `!isDebugApp \|\| !screenResumed \|\| !mockCondition` | `mockCondition` 대신 `monitor.evaluate(destination) is PlaybackDecision.Allow` |
| 3 | `setOnPreparedListener` 안 조건 (303~305행) | `screenResumed && mockCondition && hasAudioFocus` | 같은 `evaluate` 결과를 다시 확인 (준비 중 상태 변화 대비) |
| 4 | 재생 중 상태 변화 | 없음 | `monitor.addListener { ... }`에서 `evaluate`가 Hold면 `stopPlayback("…")` (출발·P단 해제 시 중단) |
| 5 | 체크박스 리스너 (206~214행) | `mockCondition` 직접 설정 | 디버그용으로 남길 경우 `monitor.setMockGear(PARK)` + `setMockLocation(목적지)`로 바꾸면 MOCK 출처가 기록됨 |
| 6 | `onDestroy` (118행) | — | `monitor.stop()` |
| 7 | 서버 보고 (API v0.2) | — | `Allow.source` → 세션 `source`, `Hold.reason.contractReason` → 이벤트 `reason` (null이면 보내지 않음) |

```kotlin
// 예시 (규혁님 코드에 아직 넣지 않음)
val decision = monitor.evaluate(Destination(message.latitude, message.longitude, message.radiusMeters))
if (decision !is PlaybackDecision.Allow) {
    updateStatus("재생 보류 · ${(decision as PlaybackDecision.Hold).detail}")
    return
}
```

### 꼭 알아야 할 동작

- **위치는 앱 화면이 보일 때만 들어옵니다.** Pleos 에뮬레이터의 위치 권한은 "앱 사용 중에만 허용"(app-op `foreground`)이라 백그라운드에서는 거부됩니다. 화면을 벗어나면 15초 뒤 `LOCATION_STALE`로 보류됩니다. 백그라운드 재생이 필요하면 위치 타입 포그라운드 서비스를 별도로 검토해야 합니다.
- 차량 OS는 다중 사용자입니다. 운전자는 user 10이며, user 0에서는 GPS가 꺼져 있습니다.
- 모든 메서드는 메인 스레드에서 호출합니다. 리스너도 메인 스레드에서 호출됩니다.
- 좌표는 판정에만 쓰고 로그·서버로 보내지 않습니다(`docs/security.md`).

## 내비(NaviHelper)를 위치에 쓰지 않는 이유

Pleos Connect v2.1.0 에뮬레이터에서 기본 내비(`ai.umos.maps.android.navigation.app`)가 에뮬레이터 NMEA를 `NMEA checksum mismatch`로 모두 거부해, `NaviHelper.getCurrentLocationInfo()` 응답의 좌표가 바뀌지 않습니다. 같은 에뮬레이터에서 Android `LocationManager` GPS는 정상 갱신됩니다. 기획서도 NaviHelper를 범용 GPS로 가정하지 않으므로 위치 판정은 Android GPS로 합니다. NaviHelper는 목적지·도착 **이벤트**(`onDestinationInfo`, `onDestinationArrived`, `onDestinationChanged`, `onRouteCancelled`) 용도로 별도 검증할 예정입니다.

## 검증 방법과 결과

빌드 명령은 [apps/vehicle/README.md](../../README.md#빌드와-검사)를 따릅니다.

```powershell
.\gradlew.bat :vehicle-state:testDebugUnitTest :vehicle-state:lintDebug
.\gradlew.bat :vehicle-state:connectedDebugAndroidTest   # 에뮬레이터 실행 중일 때
```

기기 테스트(`VehicleStateMonitorDeviceTest`)는 선택 인자로 변경 반영을 확인합니다.

```powershell
# 위치: 다른 터미널에서 1초마다 adb emu geo fix <경도> <위도> 를 보내는 동안
#       (geo fix는 한 번만 위치를 넣고, 같은 좌표를 반복하면 갱신이 오지 않을 수 있어 소수점 끝자리를 번갈아 바꿈)
adb shell am instrument -w -e class io.chamamothanmal.vehiclestate.VehicleStateMonitorDeviceTest#followsLocationChange -e expectLat 37.4979 -e expectLon 127.0276 io.chamamothanmal.vehiclestate.test/androidx.test.runner.AndroidJUnitRunner
# 기어: 실행 후 60초 안에 Virtual Console에서 D로 변경
adb shell am instrument -w -e class io.chamamothanmal.vehiclestate.VehicleStateMonitorDeviceTest#receivesGearFromPleosSdk -e expectGear NOT_PARK io.chamamothanmal.vehiclestate.test/androidx.test.runner.AndroidJUnitRunner
```

2026-11-10, Pleos Connect v2.1.0 에뮬레이터(API 34) 결과 — 실차 미검증

| 항목 | 결과 |
| --- | --- |
| JVM 단위 테스트 22개 (`PlaybackGateTest` 20, `GearMappingTest` 2) | 통과 |
| `:vehicle-state:lintDebug` | 이슈 0 |
| Pleos SDK 기어 수신 (`GEAR_PARK`, source=SDK) | 성공 |
| P단 유지 중 12초 후에도 기어 값 갱신 (주기 재조회) | 성공 |
| Android GPS 수신 (화면 표시 상태, 정확도 5m) | 성공 |
| `geo fix` 위치 변경 반영 | 성공 |
| 실제 신호 판정 (P단, 현재 위치=목적지) | `Allow(source=SDK)` |
| 시작 직후 16초 된 마지막 위치로 판정 | `Hold(LOCATION_STALE)` — 오래된 위치로 재생하지 않음 확인 |
| 기기 테스트 6개 전체 | 통과 |
| `stop()` 후 판정 | `Hold(GEAR_UNKNOWN)` |
| Virtual Console 기어 D 변경 반영 | **미확인** (user 빌드라 명령줄 VHAL 주입 불가, 수동 확인 필요) |
| 화면 없는 백그라운드 위치 수신 | 거부됨 (위 "꼭 알아야 할 동작" 참고) |

## 남은 과제

- Vehicle SDK 2.3.2의 Gear API는 지원 중단 표시("use the Google GEAR_SELECTION property")가 있습니다. `CarPropertyManager` 대체 경로는 미검증입니다.
- 도어(`Door.registerDoorPosition`), NaviHelper 목적지·도착 이벤트, CarUxRestrictions는 아직 모듈에 없습니다.
- 에뮬레이터에 `ai.pleos.playground.service.location.fused`가 설치되어 있습니다. Pleos Fused 위치 사용 가능 여부는 미확인입니다.
