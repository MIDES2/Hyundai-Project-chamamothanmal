# :vehicle-state — 차량 상태와 재생 판정 (재민)

차량 앱(`:app`)이 재생 직전에 "지금 재생해도 되는가"를 묻는 Android Library 모듈입니다.
기어·도어는 Pleos Vehicle SDK, 위치는 Android 표준 GPS, 목적지·변경·취소·도착은 Pleos NaviHelper 이벤트에서 받고,
값이 없거나 오래됐으면 재생을 허용하지 않습니다.

> 이 모듈의 결과는 **차량 상태 조건**만 판단합니다. 오디오 포커스, 서버 세션·메시지 유효성 확인은 별도로 해야 합니다.
> 에뮬레이터 결과를 실차 검증으로 표시하지 않습니다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `VehicleStateMonitor` | 진입점. 수신 시작/중지, 현재 `snapshot`, `evaluate(destination)`, 내비 이벤트 리스너, 디버그 MOCK |
| `PlaybackGate` | Android 비의존 순수 판정 함수. JVM 단위 테스트 대상 |
| `VehicleSignals.kt` | `SignalSource`(SDK/MOCK/MANUAL), `GearReading`, `DoorReading`, `LocationReading`, `NaviState`, `NaviEvent`, `VehicleSnapshot`, `Destination` |
| `source/PleosVehicleSource` | Vehicle SDK 연결 하나로 기어(변경 알림 + 3초 재조회)와 도어(지원 도어별 변경 알림 + 3초 재조회) |
| `source/AndroidGpsLocationSource` | `LocationManager` GPS 1초 간격 수신 |
| `source/PleosNaviSource` | NaviHelper 목적지(`getDestinationInfo`)와 경로 시작·목적지 변경·경로 취소·종료·도착 이벤트 |

| 신호 | Manifest 권한 (모듈에 선언됨) |
| --- | --- |
| 기어 | `pleos.car.permission.CAR_DRIVE` |
| 도어 | `pleos.car.permission.CAR_DOORS` |
| 내비 목적지·이벤트 | `pleos.car.permission.NAVI_ROUTE` (경로 검색·변경 권한 `NAVI_ROUTE_SEARCH`는 요청하지 않음) |
| 위치 | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` — 실행 중 사용자 허용 필요 (`REQUIRED_RUNTIME_PERMISSIONS`) |

## 판정 규칙 (`PlaybackGate`)

위에서부터 순서대로 확인하고 처음 걸리는 사유로 보류합니다.

| 순서 | 조건 | 결과 `HoldReason` | 서버 이벤트 reason |
| --- | --- | --- | --- |
| 1 | 기어 값 없음 / GEAR_UNKNOWN·null | `GEAR_UNKNOWN` | `STATE_UNKNOWN` |
| 2 | 기어 값이 10초보다 오래됨 또는 시각이 미래 | `GEAR_STALE` | `STATE_UNKNOWN` |
| 3 | P단 아님 | `NOT_PARKED` | `DRIVING` |
| 4 | 도어 열림 (값이 오래돼도 열림이면 이 사유) | `DOOR_OPEN` | `DOOR_OPEN` |
| 5 | 도어 값 없음 / 일부 도어 미확인 | `DOOR_UNKNOWN` | `STATE_UNKNOWN` |
| 6 | 도어 값이 10초보다 오래됨 | `DOOR_STALE` | `STATE_UNKNOWN` |
| 7 | 내비 목적지가 있고, 메시지 장소 반경 밖 | `DESTINATION_MISMATCH` | 없음 — 세션 종료(`DESTINATION_CHANGED`)로 처리 |
| 8 | 도착 확인 (아래) 실패 | `NOT_ARRIVED` / `LOCATION_*` | `NOT_ARRIVED`는 없음(PENDING 유지), `LOCATION_*`는 `STATE_UNKNOWN` |
| 9 | 판정에 쓴 값에 MOCK 포함 + `allowMock=false` | `MOCK_NOT_ALLOWED` | `STATE_UNKNOWN` |
| — | 모두 통과 | `Allow(source, arrival, distanceMeters)` | MOCK이 섞이면 `source=MOCK` |

**도착 확인**
1. GPS 값이 새것(15초 이내)·정확(100m 이내)이면 GPS로 판정합니다. 메시지 장소 반경(`radiusMeters`, 기본 100m) 안이면 `arrival=GPS`, 밖이면 `NOT_ARRIVED`. **새 GPS가 반경 밖이면 내비 도착 이벤트가 있어도 보류**합니다(신호가 엇갈리면 보류).
2. GPS를 확인할 수 없을 때만 내비 도착 이벤트를 씁니다. 내비 목적지가 메시지 장소와 맞고, 그 목적지에 대한 `onDestinationArrived`가 30분 이내면 `arrival=NAVI`. 아니면 GPS 문제 사유(`LOCATION_UNKNOWN`/`STALE`/`INACCURATE`)로 보류합니다.

- 기준값은 `GatePolicy`로 바꿀 수 있습니다(`requireDoorClosed=false`로 도어 확인을 끌 수 있으나 기획서 기준은 true). 시각은 `SystemClock.elapsedRealtime` 기준입니다.
- 기어·도어·위치 조회가 실패하면 이전 값을 즉시 지웁니다. 오래된 P단·닫힘 값으로 재생되지 않습니다.
- 목적지가 바뀌거나 경로가 취소되면 이전 도착 기록을 지웁니다.
- `evaluate()`의 기본 `allowMock`은 앱이 debuggable일 때만 true입니다. MOCK 주입(`setMockGear`, `setMockDoor`, `setMockLocation`, `setMockNavi`)도 디버그 빌드에서만 받습니다.

## 내비 이벤트와 서버 세션 (API v0.2)

`monitor.addNaviEventListener { event -> ... }`로 받습니다.

| `NaviEvent` | 앱이 할 일 |
| --- | --- |
| `DESTINATION_SET` | 출발 전 "이번 이동에서 듣기" 화면이면 `snapshot.navi`의 목적지 좌표를 세션 시작 `destinationLatitude/Longitude`로 사용 |
| `DESTINATION_CHANGED` | 재생 중단, `POST /sessions/{id}/end {reason: "DESTINATION_CHANGED"}` |
| `ROUTE_CANCELLED` | 재생 중단, `POST /sessions/{id}/end {reason: "CANCELLED"}` |
| `ARRIVED` | 재생 판정을 다시 실행 (`evaluate`) |
| `ROUTE_ENDED` | 도착 후에도 올 수 있음. 단독으로 세션을 끝내지 않음 |

## 규혁님 앱에 연결할 지점 (아직 연결하지 않음)

`:app`은 이 모듈에 아직 의존하지 않습니다. 연결할 때 바꿀 곳은 아래와 같습니다. 줄 번호는 `app/src/main/java/com/example/malttuk/MainActivity.kt` 현재 버전 기준입니다.

| # | 위치 | 현재 | 연결 방법 |
| --- | --- | --- | --- |
| 0 | `app/build.gradle.kts` dependencies | — | `implementation(project(":vehicle-state"))` |
| 1 | `onCreate` (47행) | 오디오 설정만 | `VehicleStateMonitor(this)` 생성, `REQUIRED_RUNTIME_PERMISSIONS` 요청 후 `start()` |
| 2 | `startTestPlayback()` 첫 조건 (264행) | `!isDebugApp \|\| !screenResumed \|\| !mockCondition` | `mockCondition` 대신 `monitor.evaluate(destination) is PlaybackDecision.Allow` |
| 3 | `setOnPreparedListener` 안 조건 (303~305행) | `screenResumed && mockCondition && hasAudioFocus` | 같은 `evaluate` 결과를 다시 확인 (준비 중 상태 변화 대비) |
| 4 | 재생 중 상태 변화 | 없음 | `monitor.addListener { ... }`에서 `evaluate`가 Hold면 `stopPlayback("…")` (문 열림·출발 시 즉시 중단) |
| 5 | 내비 이벤트 | 없음 | `monitor.addNaviEventListener { ... }` — 위 표대로 중단·세션 종료 |
| 6 | 체크박스 리스너 (206~214행) | `mockCondition` 직접 설정 | 디버그용으로 남길 경우 `setMockGear(PARK)`·`setMockDoor(CLOSED)`·`setMockLocation(목적지)`로 바꾸면 MOCK 출처가 기록됨 |
| 7 | `onDestroy` (118행) | — | `monitor.stop()` |
| 8 | 서버 보고 (API v0.2) | — | `Allow.source` → 세션·이벤트 `source`, `Hold.reason.contractReason` → 이벤트 `reason` (null이면 보내지 않음) |

```kotlin
// 예시 (규혁님 코드에 아직 넣지 않음)
val decision = monitor.evaluate(Destination(message.latitude, message.longitude, message.radiusMeters))
if (decision !is PlaybackDecision.Allow) {
    updateStatus("재생 보류 · ${(decision as PlaybackDecision.Hold).detail}")
    return
}
```

### 꼭 알아야 할 동작

- **위치는 앱 화면이 보일 때만 들어옵니다.** Pleos 에뮬레이터의 위치 권한은 "앱 사용 중에만 허용"(app-op `foreground`)이라 백그라운드에서는 거부됩니다. 화면을 벗어나면 15초 뒤 `LOCATION_STALE`로 보류됩니다(내비 도착 이벤트가 있으면 그것으로 판정). 백그라운드 재생이 필요하면 위치 타입 포그라운드 서비스를 별도로 검토해야 합니다.
- 차량 OS는 다중 사용자입니다. 운전자는 user 10이며, user 0에서는 GPS가 꺼져 있습니다.
- 모든 메서드는 메인 스레드에서 호출합니다. 리스너도 메인 스레드에서 호출됩니다.
- 좌표는 판정에만 쓰고 로그·서버로 보내지 않습니다(`docs/security.md`). 내비 목적지 이름·주소는 저장하지 않습니다.

## 내비(NaviHelper) 위치를 쓰지 않는 이유

Pleos Connect v2.1.0 에뮬레이터에서 기본 내비(`ai.umos.maps.android.navigation.app`)가 위치용 NMEA를 모두 거부해 `NaviHelper.getCurrentLocationInfo()` 좌표가 바뀌지 않습니다(`NmeaParser.validateChecksum`: `NMEA checksum mismatch`).

2026-11-10 시험: 체크섬을 직접 계산한 `$GPGGA`·`$GPRMC`를 `adb emu geo nmea`로 보내도 같은 오류가 났습니다. 오류의 `expected` 값이 보낸 문장과 상관없이 항상 `47`이어서, 내비가 받는 문장은 앱이 보낸 원문이 아니거나(에뮬레이터 GNSS 계층이 다시 생성) 기본 내비 파서가 체크섬을 잘못 읽는 것으로 보입니다. **앱 코드로는 고칠 수 없는 에뮬레이터·기본 내비 쪽 문제**이며, 내비 현재 위치는 18km 떨어진 기존 값에서 움직이지 않았습니다.

그래서 위치·도착은 Android GPS로 판정하고, NaviHelper는 목적지·변경·취소·도착 **이벤트**에 씁니다. 기획서도 NaviHelper를 범용 GPS로 가정하지 않습니다. 같은 이유로 에뮬레이터에서는 내비가 이동을 인식하지 못해 `onDestinationArrived` 발생은 확인하지 못했습니다.

## 검증 방법과 결과

빌드 명령은 [apps/vehicle/README.md](../../README.md#빌드와-검사)를 따릅니다.

```powershell
.\gradlew.bat :vehicle-state:testDebugUnitTest :vehicle-state:lintDebug
.\gradlew.bat :vehicle-state:connectedDebugAndroidTest   # 에뮬레이터 실행 중일 때
```

기기 테스트(`VehicleStateMonitorDeviceTest`)는 선택 인자로 변경 반영을 확인합니다. 내비 테스트는 테스트 APK에만 있는 `NAVI_ROUTE_SEARCH` 권한으로 경로를 요청·취소합니다.

```powershell
# 위치: 다른 터미널에서 1초마다 adb emu geo fix <경도> <위도> 를 보내는 동안
#       (geo fix는 한 번만 위치를 넣고, 같은 좌표를 반복하면 갱신이 오지 않을 수 있어 소수점 끝자리를 번갈아 바꿈)
adb shell am instrument -w -e class io.chamamothanmal.vehiclestate.VehicleStateMonitorDeviceTest#followsLocationChange -e expectLat 37.4979 -e expectLon 127.0276 io.chamamothanmal.vehiclestate.test/androidx.test.runner.AndroidJUnitRunner
# Virtual Console 열기: adb forward tcp:8088 tcp:8088 후 PC 브라우저에서 http://127.0.0.1:8088/ → "Emulation Controls"
# 기어: 실행 후 60초 안에 Virtual Console에서 D로 변경 (Gear Selection)
adb shell am instrument -w -e class io.chamamothanmal.vehiclestate.VehicleStateMonitorDeviceTest#receivesGearFromPleosSdk -e expectGear NOT_PARK io.chamamothanmal.vehiclestate.test/androidx.test.runner.AndroidJUnitRunner
# 도어: 실행 후 60초 안에 Virtual Console에서 운전석 도어 열기 (Door Position › Front left › Open)
adb shell am instrument -w -e class io.chamamothanmal.vehiclestate.VehicleStateMonitorDeviceTest#receivesDoorFromPleosSdk -e expectDoor OPEN io.chamamothanmal.vehiclestate.test/androidx.test.runner.AndroidJUnitRunner
```

2026-11-10, Pleos Connect v2.1.0 에뮬레이터(API 34) 결과 — 실차 미검증

| 항목 | 결과 |
| --- | --- |
| JVM 단위 테스트 46개 (`PlaybackGateTest` 37, `PleosSourceMappingTest` 9) | 통과 |
| `:vehicle-state:lintDebug` | 이슈 0 |
| 기기 테스트 9개 전체 (선택 인자 없는 기본 실행) | 통과 |
| Pleos SDK 기어 수신 (`GEAR_PARK`, source=SDK), P단 유지 중 값 갱신 | 성공 |
| Pleos SDK 도어 수신 (지원 도어 6개, `CLOSED`, 운전석 범위 0..1) | 성공 |
| Android GPS 수신(화면 표시 상태, 정확도 5m), `geo fix` 위치 변경 반영 | 성공 |
| 내비 경로 요청 → 목적지 좌표 수신, `DESTINATION_SET` | 성공 |
| 내비 목적지와 다른 장소 메시지 판정 | `Hold(DESTINATION_MISMATCH)` |
| 내비 경로 취소 → 목적지 해제, `ROUTE_CANCELLED` | 성공 |
| 실제 신호 판정 (P단, 도어 닫힘, 현재 위치=목적지) | `Allow(source=SDK, arrival=GPS)` |
| 시작 직후 16초 된 마지막 위치로 판정 | `Hold(LOCATION_STALE)` |
| `stop()` 후 판정 | `Hold(GEAR_UNKNOWN)` |
| Virtual Console 기어 P → D 변경 반영 (Emulation Controls › Gear Selection) | 성공 — 약 4초 안에 `NOT_PARK` |
| Virtual Console 운전석 도어 열림 반영 (Emulation Controls › Door Position › Front left › Open) | 성공 — 약 4초 안에 `OPEN [ROW_1_LEFT]`, 판정 `Hold(DOOR_OPEN)` |
| SDK `setDoorPosition`으로 도어 열기 | 성공 응답하지만 에뮬레이터 값이 바뀌지 않음 (Virtual Console로만 변경 가능) |
| 내비 도착 이벤트 `onDestinationArrived` | **미확인** (에뮬레이터 내비가 이동을 인식하지 못함) |
| 체크섬 맞춘 NMEA로 내비 위치 갱신 | 실패 (위 "내비 위치를 쓰지 않는 이유") |

## 남은 과제

- Vehicle SDK 2.3.2의 Gear API는 지원 중단 표시("use the Google GEAR_SELECTION property")가 있습니다. `CarPropertyManager` 대체 경로는 미검증입니다.
- CarUxRestrictions(주행 중 화면·입력 제한)는 아직 모듈에 없습니다.
- 에뮬레이터에 `ai.pleos.playground.service.location.fused`가 설치되어 있습니다. Pleos Fused 위치 사용 가능 여부는 미확인입니다.
