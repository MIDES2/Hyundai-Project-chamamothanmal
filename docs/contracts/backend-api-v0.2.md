# 차량 가족 메시지 API v0.2

최종 기획서 v3를 반영한 /api/v2 규격입니다. Spring Boot 유지, local 또는 Supabase 백엔드 사용. v1 수신자·날짜 API와 호환되지 않습니다. 실제 앱·SDK는 별도 연결합니다.

## 공통

- 모든 /api/v2 요청: `Authorization: Bearer <사용자 토큰>`. local은 가상 토큰, supabase는 로그인 access_token입니다.
- 차량 전용 요청은 추가로 `X-Vehicle-Token: <연결 시 한 번 반환된 토큰>`이 필요합니다. 해당 로그인 사용자·차량 연결에 묶입니다. 원문은 DB에 저장하지 않습니다.
- ID는 UUID. 시각은 UTC epoch milliseconds 정수입니다. 만료는 날짜가 아닌 절대 시각으로 계산합니다.
- 에러는 `{ "error": { "code": "...", "message": "..." } }`. 입력 오류400, 인증401, 금지403, 없음/다른 권한404, 충돌409, 만료410, 음성415/413/422, 원격 장애503.
- 실시간 위치 궤적은 저장하지 않습니다. 메시지 장소와 사용자가 선택한 이동 목적지만 저장합니다.
- SDK/MOCK/MANUAL은 클라이언트 보고 출처입니다. 서버가 차량 상태의 진위를 증명하지 않습니다.

## 장소 검색 (2026-11-11 추가)

GET /places?query=검색어&type=keyword&page=1&size=10. 사용자 인증 필요, 차량 토큰 불필요. type keyword/주소 address, query1~100자, page1~45,size1~15. 응답 items에 provider,id,placeName,address,roadAddress,lotAddress,latitude,longitude를 반환하고 type,page,size,totalCount,pageableCount,hasNext를 제공합니다. 카카오 x는 longitude, y는 latitude입니다. 서버 키가 없으면503 PLACE_SEARCH_NOT_CONFIGURED이며 임의 결과로 대체하지 않습니다. 웹이 사용자의 선택을 받은 뒤 메시지 등록에 장소·주소·좌표를 보냅니다. [전체 안내](../../services/api/PLACE_SEARCH.md)

## 가족과 차량

| 메서드·주소 | 요청 | 응답/의미 |
| --- | --- | --- |
| GET /me | 없음 | userId, families |
| POST /families | {name} | 201 familyId, name. 생성자가 관리자 |
| GET /families/{id}/members | 관리자 | items: userId, canSend, active |
| POST /families/{id}/invitations | 관리자 | code, expiresAt |
| POST /families/join | {code} | familyId. 승인 초대로 가입하면 canSend=true |
| PATCH /families/{id}/members/{userId} | 관리자, {canSend:false} | 204. 향후 등록 권한 변경 |
| DELETE /families/{id}/members/{userId} | 관리자 | 204. 구성원·차량 연결·세션 차단, 해당 가족 차량에 보낸 기존 음성도 삭제 처리 |
| POST /vehicles | 관리자, {familyId,name} | 201 vehicleId, familyId, name |
| GET /families/{id}/vehicles | 가족 구성원 | items: vehicleId,name,revision |
| POST /vehicles/{id}/pairing-codes | 관리자 | code, expiresAt |
| POST /vehicles/pair | {code}, 해당 가족 구성원 | vehicleId, deviceId, vehicleToken |
| GET /vehicles/{id}/access | 사용자+차량 토큰 | vehicleId,revision,checkedAt,cacheAction |
| DELETE /vehicles/{id}/connection | 관리자 | 204. 차량 비활성화·기기 토큰 폐기·세션 종료·해당 음성 삭제 처리 |
| DELETE /me | 본인 | 204. 앱 접근 즉시 차단, 음성 삭제·세션 정리, Auth 삭제 작업 예약 |

코드는 무작위 256-bit URL-safe 문자열, 10분 만료, 한 번만 사용합니다. 짧은 숫자 코드를 사용하지 않습니다. 관리자만 발급하며 DB에는 해시만 저장합니다. 구성원 제거 시 기존 미사용 코드를 모두 무효화합니다. 이미 활성인 구성원은 초대 재사용으로 발신 금지를 해제할 수 없습니다. 가족 관리자 자신을 구성원 삭제 API로 제거할 수 없습니다. 재연결하면 이전 기기·세션은 폐기됩니다. 초기 버전은 차량당 활성 설치 한 개이며 로그인 운전자 변경 시 다시 연결해야 합니다. 자동 운전자 식별·관리자 이양은 미구현입니다.

## 음성 메시지

POST /messages: multipart/form-data + `Idempotency-Key: <UUID>`.

| 필드 | 규격 |
| --- | --- |
| vehicleId | 허용된 가족 차량 ID |
| placeName | 필수 1~120자, 공백만 불가 |
| address | 선택 최대500자, 선택한 장소 주소. 생략 가능 |
| latitude / longitude | 필수 유한 숫자, -90~90 / -180~180 |
| radiusMeters | 기본100, 10~1000m |
| expiresInHours | 기본24, 1~24시간 |
| audio | 실제 PCM WAV, 8/16-bit·1/2채널·8~48kHz, 최대15초·5MiB |

응답: 201 신규/200 동일 재시도 `{messageId,replayed,expiresAt}`. 같은 발신자·같은 키의 다른 내용409. 수신자 ID·scheduledDate는 v2에서 사용하지 않습니다. 녹음 형식 변환은 웹 담당자와 합의가 필요합니다.

- GET /messages/sent: 본인의 삭제되지 않은 최근 최대100개 메시지, items.
- GET /messages/{id}: 발신자 본인만 상세 조회. messageId,senderId,vehicleId,placeName,address,latitude,longitude,radiusMeters,requireParked=true,audioBytes,audioSeconds,createdAt,expiresAt,completedAt,deletedAt,deliveries,replies. 저장 경로·음성 원문·정확한 차량 현재 위치는 반환하지 않습니다.
- DELETE /messages/{id}: 발신자가 취소·삭제, 204. 즉시 새 접근을 차단하고 정리 작업에서 파일 삭제.
- GET /vehicles/{id}/messages: 차량 토큰 필요. 유효·미완료 메시지 후보 최대100개. 수신 선택 전에는 목록 메타데이터만 제공하며 음성은 이동 연결 후에만 다운로드합니다.

## 이번 이동에서 듣기

POST /vehicles/{id}/sessions: 차량 토큰 필요.

```json
{
  "requestId":"b393360e-e41f-45d2-a101-e84d42012ef9",
  "consent":true,
  "parkedConfirmed":true,
  "destinationLatitude":37.0,
  "destinationLongitude":127.0,
  "source":"MOCK",
  "messageIds":["7a7dd716-d041-4bd6-b616-38ea49cc6ee7"]
}
```

출발 전 사용자 수신 선택과 주차 확인 후에만 호출합니다. 서버는 true 보고를 검사하지만 실제 주차 신호 확인은 차량 앱 책임입니다. SDK 검증 전에는 MOCK입니다. 목적지 좌표와 메시지 장소의 거리가 설정 반경 이내여야 합니다. 이는 목적지 일치 확인이며 현재 차의 도착 판정은 아닙니다. 초기 버전은 차량 하나·목적지 하나·메시지 하나로 제한합니다.

응답 `{sessionId,messageIds,replayed}`. 같은 requestId·같은 내용 재시도는 동일 이동을 반환합니다. 신규 이동은 이전 활성 이동을 종료합니다. 이동은 최대24시간이며 잊힌 세션도 만료 처리합니다. 완료한 메시지는 다른 이동에서 다시 연결하지 않습니다.

- GET /sessions/{id}: 현재 로그인·연결 기기만 조회, active·endReason·deliveries(messageId,status,version).
- POST /sessions/{id}/end: `{reason:"DESTINATION_CHANGED"}` 등, 204. ARRIVED/DESTINATION_CHANGED/CANCELLED/PROFILE_CHANGED/DISCONNECTED. 목적지 변경·취소·프로필 변경 시 앱이 즉시 중단·캐시 정리하고 서버에 종료 보고합니다. 이미 종료된 이동을 다시 시작하지 않습니다.
- GET /sessions/{sid}/messages/{mid}: 재생 직전 온라인 유효성 확인. 활성 이동·권한·만료 검사 후 메시지 메타데이터와 playback(status,version) 반환. COMPLETED/SKIPPED이면 새 재생 금지.
- GET /sessions/{sid}/messages/{mid}/audio: 활성 이동에 연결된 유효·미완료 메시지만 200 audio/wav. 매 요청 권한 확인. 공개·서명 URL 없음.

## 재생 이벤트와 답장

POST /sessions/{sid}/messages/{mid}/events:

```json
{"eventId":"03229a38-939b-4948-a21f-ae273f3b19e0","status":"HELD","expectedVersion":0,"source":"MOCK","reason":"STATE_UNKNOWN"}
```

응답 `{eventId,status,version}`. 같은 이벤트 ID·같은 내용은 같은 결과를 반환하며 버전을 증가시키지 않습니다. 오래된 버전 VERSION_CONFLICT409, 같은 ID의 다른 내용 EVENT_ID_CONFLICT409. 충돌 시 이동 상세를 다시 조회하고 자동 재생하지 않습니다.

| 현재 | 다음 상태 |
| --- | --- |
| PENDING | PLAYING, HELD, FAILED, SKIPPED |
| PLAYING | PAUSED, COMPLETED, HELD, FAILED, SKIPPED |
| PAUSED / HELD / FAILED | PLAYING 가능; HELD/FAILED/SKIPPED는 같은 상태 반복 제외 |
| COMPLETED / SKIPPED | 새 변경 없음, 동일 이벤트 재시도만 허용 |

reason: USER_ACTION/STATE_UNKNOWN/DRIVING/DOOR_OPEN/AUDIO_FOCUS_LOST/PLAYBACK_ERROR/NETWORK_ERROR. COMPLETED는 실제 플레이어 완료 콜백에서만 보고합니다. 재생 시작 전 도착·주차·문 닫힘·오디오·UX 제한을 차량 앱이 확인하고, 권한 확인 실패/오프라인에서는 새 재생을 보류합니다. 서버 응답은 재생 허가나 실제 청취자 확인이 아닙니다. 사용자 중지 후 자동 재개하지 않습니다.

POST /sessions/{sid}/messages/{mid}/replies:

```json
{"replyId":"17950fe9-bf1d-4b01-8d1a-4c80e52d1701","kind":"THANK_YOU","parkedConfirmed":true}
```

완료한 메시지에만 주차 상태에서 답장합니다. THANK_YOU=고마워요, ARRIVED_SAFELY=잘 도착했어요. 이동·메시지별 답장 한 개, 같은 ID·종류 재시도 허용, 다른 내용409. 발신자는 상세의 replies에서 확인합니다. 차량 마이크 음성 답장은 미구현입니다.

## 만료·삭제와 클라이언트 책임

기본 만료24시간. 즉시 접근 차단과 물리 파일 삭제는 구분합니다. 서버 정리 작업이 기본60초 주기로 만료·삭제 음성을 제거하고 실패 시 재시도합니다. 완료 기록은 완료 후30일, 미완료는 만료 후30일 기준으로 정리합니다. 앱 탈퇴 시 Auth 삭제 작업을 비동기로 재시도하며 실제 원격 삭제는 별도 검증합니다. 탈퇴 차단용 user ID 기록은 유지합니다.

차량은 로그인 변경·연결 해제·권한 오류 시 자체 캐시를 지웁니다. 서버가 오프라인 차량의 캐시를 강제 삭제할 수는 없습니다. 재생 직전에 access와 세션/메시지 유효성을 온라인으로 확인하고, 상태가 불명확하면 보류합니다.

[팀 연동 안내](../../services/api/TEAM_INTEGRATION.md) · [Supabase 설정](../../services/api/SUPABASE_SETUP.md)
