# 연동 계약 — 최종 기획서 반영

**현재 구현은 [차량 가족 API v0.2](backend-api-v0.2.md)의 /api/v2입니다.** 아래 v0.1은 이전 수신자 기반 초안으로 보존하며 새 앱 연동에 사용하지 않습니다. Supabase 연결과 직접 접근 전환은 [설정 가이드](../../services/api/SUPABASE_SETUP.md)를 참고합니다.

구현 예제와 담당별 연결은 [백엔드 팀 연동 안내](../../services/api/TEAM_INTEGRATION.md)를 참고합니다. Java 서버와 Kotlin 차량 앱은 공통 HTTP 규격으로 연결합니다.

2026-11-08 서버 1차 구현의 구체적인 요청·응답·재생 상태는 [메시지 서버 구현 규격 v0.1](backend-api-v0.1.md)에 있습니다. 초기 초안과 함께 검토하며 구현 선택이 팀 전체의 최종 합의를 의미하지 않습니다. Idempotency-Key, PCM WAV 제한, 시험 토큰과 재생 이벤트 규격을 추가했습니다.

아래는 합의 항목이며 확정된 API 명세가 아니다. 담당자가 실제 구현 전에 요청·응답 예제와 실패 예제를 추가한다.

| 경계 | 합의할 항목 | 담당 |
| --- | --- | --- |
| 웹 → 서버 | 등록 URL·필드·시간대·좌표·수신자·인증·파일 형식/크기 제한 | 나경·유림 |
| 서버 → 차량 | 메시지 ID·조회 조건·파일 접근권한·다운로드·재생 결과 갱신 | 유림·규혁 |
| 상태 → 재생 | 이벤트 ID·발생 시각·실제/mock 출처·도착/주차/알 수 없음·허용/보류 | 재민·규혁·민석 |
| 공통 오류 | 성공/실패/보류·재시도·중복 요청 및 재생 방지·오류 코드 | 전원 |

## SDK 지원 확인표

2026-11-10 기준, Pleos Connect v2.1.0 에뮬레이터(API 34)에서 확인했습니다. 실차는 검증하지 않았습니다. 구현은 [`:vehicle-state`](../../apps/vehicle/android/vehicle-state/README.md) 모듈에 있습니다.

| 신호 | 사용 API | 지원 여부 | 권한 | 에뮬레이터 검증 | 대체 mock |
| --- | --- | --- | --- | --- | --- |
| 위치 | Android `LocationManager` GPS (1초) | 지원 | `ACCESS_FINE_LOCATION` (실행 중 허용, **앱 사용 중에만**) | 수신·`geo fix` 변경 반영 성공. 화면이 없으면 거부됨 | `setMockLocation` (디버그 빌드) |
| 위치 (내비) | NaviHelper `getCurrentLocationInfo` | 응답은 옴 | `pleos.car.permission.NAVI_ROUTE` | 좌표 갱신 안 됨. 기본 내비가 NMEA를 체크섬 불일치로 거부하며, 체크섬을 맞춘 `geo nmea`로도 해결 안 됨(에뮬레이터·기본 내비 문제). 위치 판정에 쓰지 않음 | — |
| 내비 목적지 | NaviHelper `getDestinationInfo` → `onDestinationInfo` | 지원 | `pleos.car.permission.NAVI_ROUTE` | 경로 요청 후 목적지 좌표 수신 성공, 다른 장소 메시지는 `DESTINATION_MISMATCH` 보류 | `setMockNavi` (디버그 빌드) |
| 내비 변경·취소 | `onDestinationChanged`·`onRouteCancelled`·`onRouteEnded` | 지원 | `pleos.car.permission.NAVI_ROUTE` | 경로 취소 → `ROUTE_CANCELLED` 이벤트·목적지 해제 성공. 목적지 변경은 미실행 | — |
| 내비 도착 | `onDestinationArrived` | SDK에 이벤트 존재 | `pleos.car.permission.NAVI_ROUTE` | 미확인 (에뮬레이터 내비가 이동을 인식하지 못함). GPS 미확인 시 보조 근거로만 사용 | `setMockNavi(..., arrived=true)` |
| 주차·P단 | Vehicle `Gear` 변경 알림 + 3초 재조회 | 지원 (SDK 2.3.2에서 지원 중단 표시, `GEAR_SELECTION` 프로퍼티 권장) | `pleos.car.permission.CAR_DRIVE` | `GEAR_PARK` 수신, Virtual Console P→D 변경 반영 성공 | `setMockGear` (디버그 빌드) |
| 도어 | Vehicle `Door` 지원 도어별 변경 알림 + 3초 재조회 | 지원 (에뮬레이터 지원 도어 6개) | `pleos.car.permission.CAR_DOORS` | `CLOSED` 수신, Virtual Console 운전석 열림 반영·`DOOR_OPEN` 보류 성공 (SDK `setDoorPosition`은 성공 응답하나 값 불변) | `setMockDoor` (디버그 빌드) |

## 상태 → 재생 판정 (재민 제안, 규혁·민석 검토 필요)

판정 순서: 기어 → 도어 → 내비 목적지 일치 → 도착 → 출처. 상세는 [`:vehicle-state` README](../../apps/vehicle/android/vehicle-state/README.md#판정-규칙-playbackgate).

- 출처: 실제 콜백은 `SDK`, 앱이 주입한 값은 `MOCK`. 판정에 쓴 값 중 하나라도 MOCK이면 결과 `source`는 `MOCK`이며, 릴리스 빌드에서는 MOCK을 허용하지 않습니다.
- 기어 값이 없음·GEAR_UNKNOWN·10초 초과면 보류(`STATE_UNKNOWN`), P단이 아니면 보류(`DRIVING`).
- 도어가 하나라도 열리면 보류(`DOOR_OPEN`), 도어 값이 없음·일부 미확인·10초 초과면 보류(`STATE_UNKNOWN`).
- 내비 목적지가 메시지 장소 반경 밖이면 보류하고 이벤트 대신 세션 종료(`DESTINATION_CHANGED`)로 처리합니다.
- 도착: 새 GPS(15초 이내, 정확도 100m 이내)가 메시지 장소 `radiusMeters` 안이면 도착. 반경 밖이면 내비 도착 이벤트가 있어도 도착 전(PENDING 유지, 이벤트 없음). GPS를 확인할 수 없을 때만 일치하는 목적지의 30분 이내 내비 도착 이벤트를 인정하고, 그것도 없으면 보류(`STATE_UNKNOWN`).
- 목적지 변경·경로 취소 이벤트는 앱에 즉시 전달되며, 앱은 재생을 멈추고 세션 종료(`DESTINATION_CHANGED`/`CANCELLED`)를 보고합니다.
- 조회 실패 시 이전 값을 지워, 오래된 P단·닫힘 값으로 재생하지 않습니다.
- 이 판정은 차량 상태만 다룹니다. 오디오 포커스·서버 세션/메시지 유효성은 별도로 확인합니다.

미확인·오래된 차량 상태의 처리와 주행 중 기능 제한을 명시한다. SDK 지원 여부를 코드나 문서에서 추정해 보장하지 않는다.

## 공통 계약 검토

- 등록·조회·다운로드·재생 갱신마다 호출자와 필요한 권한을 명시한다.
- 메시지 ID, 날짜·시간대, 재시도 시 중복 처리, 실패 응답 예시를 맞춘다.
- 모의 이벤트와 실차 이벤트를 구별하며 정확한 도착 시각이나 위치를 발신자에게 노출할 필요가 있는지 검토한다.
- [보안과 데이터 처리](../security.md)

## 음성 메시지 연동 규격 v0.1 — 팀 검토용 초안

### 목표

모바일 웹에서 등록한 샘플 음성 한 건을 차량 앱이 조회하고,
같은 `messageId`로 파일을 받아 수동 재생한다.

### 1. 모바일 웹 → 서버: 음성 메시지 등록

`POST /api/v1/messages`

요청 형식은 `multipart/form-data`로 제안한다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `recipientId` | 문자열 | 예 | 수신자 ID. 1차 통합에서는 팀이 정한 테스트 계정을 사용한다. |
| `placeName` | 문자열 | 예 | 발신자가 선택한 장소의 표시 이름. |
| `latitude` | 숫자 | 예 | 장소 위도. 범위는 -90~90. |
| `longitude` | 숫자 | 예 | 장소 경도. 범위는 -180~180. |
| `scheduledDate` | 문자열 | 예 | 발신자가 선택한 날짜. 형식은 `YYYY-MM-DD`. |
| `audio` | 파일 | 예 | 음성 파일. 지원 형식과 최대 크기는 웹·서버·차량 앱 담당자가 합의한다. |

등록 성공 시 서버는 `201 Created`와 함께 메시지 ID를 반환한다.

```json
{
  "messageId": "msg-001"
}
```

`messageId`는 서버가 발급하는 필수 문자열이다. `msg-001`은 예시이며,
웹과 차량 앱은 ID의 형식을 해석하거나 직접 생성하지 않는다.

### 2. 서버 → 차량 앱: 조회 및 다운로드

`GET /api/v1/messages`

서버는 요청자의 신원을 확인하고, 해당 수신자에게 허용된 메시지만 반환한다.

```json
{
  "items": [
    {
      "messageId": "msg-001",
      "placeName": "테스트 주차장",
      "scheduledDate": "2026-11-06"
    }
  ]
}
```

`GET /api/v1/messages/{messageId}/audio`

차량 앱은 조회에서 받은 `messageId`로 음성을 요청한다.
서버는 다운로드 요청에서도 해당 메시지에 대한 수신자 권한을 확인한다.

| 상황 | 응답 제안 |
| --- | --- |
| 다운로드 성공 | `200 OK`와 음성 파일, 실제 파일 형식에 맞는 `Content-Type` |
| 인증 정보 없음 또는 유효하지 않음 | `401 Unauthorized` |
| 메시지가 없거나 해당 수신자가 접근할 수 없음 | `404 Not Found` |

### 3. 첫 통합 테스트 (`INT-01`)

1. 테스트 발신자가 샘플 음성 한 건을 등록하고 `messageId`를 기록한다.
2. 테스트 수신자로 차량 앱에서 조회했을 때 같은 `messageId`가 나오는지 확인한다.
3. 그 ID로 파일을 받아 수동 재생하고, 등록한 음성이 맞는지 확인한다.
4. 다른 테스트 수신자가 같은 ID로 다운로드를 요청하면 거부되는지 확인한다.

### PR에서 합의할 항목

- 나경·유림·규혁: 웹에서 생성하고 차량 앱에서 재생할 수 있는 음성 형식과 최대 크기.
- 유림·민석: 발신자·수신자 인증 방식과 오류 응답 형식.
- 나경·유림·규혁: `scheduledDate`를 해석할 시간대와 날짜에 따른 조회·재생 동작.
- 재민·규혁·민석: 도착·주차 이벤트와 자동 재생 조건. 첫 수동 재생 통합 이후 별도 규격으로 정한다.
