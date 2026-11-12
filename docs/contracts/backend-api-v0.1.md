# 메시지 서버 구현 규격 v0.1

2026-11-08 1차 Java 서버 구현 기준입니다. 최종 팀 합의가 아니며, 웹·차량·QA가 음성 코덱·인증·수신자 모델·날짜·상태 규칙을 검토해야 합니다.

## 공통

- PC 기본 주소 `http://127.0.0.1:8000`. 모든 /api/** 요청은 Authorization: Bearer <개인 시험 토큰> 필수.
- 사용자 ID는 토큰으로 서버에서 결정합니다. 시험 발신자는 승인된 두 시험 수신자에게만 등록합니다.
- 수신함·상세·다운로드·재생 갱신은 해당 수신자만 허용. 없거나 남의 메시지는 404입니다.
- 공개 /health는 `{"status":"UP"}`만 반환. 토큰·음성·정밀 좌표는 로그에 넣지 않습니다.

## 등록

POST /api/v1/messages, multipart/form-data. Idempotency-Key 헤더에 등록 요청별 UUID를 전달합니다.

| 필드 | 규격 |
| --- | --- |
| recipientId | 필수 문자열, 승인된 demo-recipient 또는 demo-other |
| placeName | 필수, 공백만 불가·최대120자 |
| latitude / longitude | 필수 유한 숫자, -90~90 / -180~180 |
| scheduledDate | 필수 YYYY-MM-DD, API_TIMEZONE 기준 오늘 이후 |
| radiusMeters | 선택 정수, 기본100·10~1000m |
| audio | 필수 PCM WAV, 8/16-bit·mono/stereo·8~48kHz, 최대15초·5MiB |

MIME은 audio/wav, audio/x-wav, audio/wave이며 실제 구조도 검사합니다. WebM/MP3/M4A와 이름만 WAV로 바꾼 파일은 지원하지 않습니다. 원래 업로드 파일명은 저장 경로로 사용하지 않습니다.

새 등록 201, 동일 발신자·동일 키·동일 내용 재시도 200:

```json
{"messageId":"e91843dd-2b1e-4f25-9dfd-d3de7f021c4b"}
```

응답 Location은 상세 상대 경로, Idempotency-Replayed는 true/false입니다. 같은 키의 다른 내용은 409 IDEMPOTENCY_CONFLICT입니다. UUID는 서버가 발급하며 앱은 의미를 해석하지 않습니다.

## 조회와 파일 수신

GET /api/v1/messages?scheduledDate=2026-11-08&offset=0&limit=20

날짜 필터 선택, offset 기본0 이상, limit 기본20·1~100. 날짜 미지정 시 미래 날짜를 포함한 본인 메시지 전체를 생성 시각 내림차순으로 조회합니다. 전체 이동 궤적은 수집하지 않습니다.

```json
{
  "items": [{
    "messageId": "e91843dd-2b1e-4f25-9dfd-d3de7f021c4b",
    "placeName": "Synthetic test place",
    "latitude": 37.0,
    "longitude": 127.0,
    "scheduledDate": "2026-11-08",
    "playbackConditions": {"radiusMeters": 100, "requireParked": true},
    "audioDurationSeconds": 1.0,
    "audioContentType": "audio/wav",
    "audioSizeBytes": 32044,
    "audioPath": "/api/v1/messages/e91843dd-2b1e-4f25-9dfd-d3de7f021c4b/audio",
    "playback": {"status": "PENDING", "version": 0}
  }],
  "offset": 0,
  "limit": 20,
  "total": 1
}
```

GET /api/v1/messages/{messageId}는 items의 항목 하나를 반환합니다.

GET /api/v1/messages/{messageId}/audio는 200 audio/wav 바이너리를 반환합니다. audioPath는 공개 링크가 아니며 다운로드마다 권한을 확인합니다. 미리 받기는 가능하나 다운로드가 완료·청취 확인을 뜻하지 않습니다. 파일이 사라진 경우 503 AUDIO_UNAVAILABLE입니다. 서버 파일 경로나 원래 파일명은 응답하지 않습니다.

## 재생 결과

POST /api/v1/messages/{messageId}/playback-events, application/json.

```json
{
  "eventId": "c1cb522a-d036-471c-b9a6-37383f080725",
  "status": "PLAYING",
  "expectedVersion": 0,
  "source": "MANUAL",
  "reason": "USER_ACTION"
}
```

- eventId: 필수 UUID. 동일 이벤트 재시도에 재사용.
- status: 필수, 아래 전이표 참고.
- expectedVersion: 필수 0 이상, 조회한 playback.version.
- source: 필수 MANUAL / SDK / MOCK. 클라이언트 보고값이며 실제 신호 증명은 아님.
- reason: 선택 USER_ACTION / STATE_UNKNOWN / DRIVING / DOOR_OPEN / AUDIO_FOCUS_LOST / NETWORK_ERROR / PLAYBACK_ERROR.

성공 200:

```json
{
  "messageId": "e91843dd-2b1e-4f25-9dfd-d3de7f021c4b",
  "eventId": "c1cb522a-d036-471c-b9a6-37383f080725",
  "status": "PLAYING",
  "version": 1
}
```

새 이벤트마다 version이 1 증가합니다. 동일 eventId·동일 내용은 저장 당시 응답을 반환하며 증가하지 않습니다. 오래된 재시도 응답으로 최신 앱 상태를 덮어쓰지 않습니다. 같은 eventId의 다른 내용은 EVENT_ID_CONFLICT, 오래된 버전은 VERSION_CONFLICT이며 둘 다 409입니다. 상세를 다시 조회하고 새 이벤트가 필요한지 판단합니다. 한 차량 앱에서 메시지별 이벤트를 순서대로 보내며 서버는 도착 순서를 임의 재정렬하지 않습니다.

| 현재 상태 | 허용할 다음 상태 |
| --- | --- |
| PENDING | PLAYING, HELD, FAILED, SKIPPED |
| PLAYING | PAUSED, COMPLETED, HELD, FAILED, SKIPPED |
| PAUSED | PLAYING, HELD, FAILED, SKIPPED |
| HELD | PLAYING, FAILED, SKIPPED |
| FAILED | PLAYING, HELD, SKIPPED |
| COMPLETED / SKIPPED | 새 전이 없음. 동일 eventId 재시도만 허용 |

PLAYING은 API_TIMEZONE의 오늘이 scheduledDate와 같을 때만 허용합니다. 차량은 미래 메시지를 내려받아도 시작 기록을 미리 남기지 않습니다. 서버가 주차·도착·문·UX·오디오를 확인하지 않으므로 차량 앱이 재생 직전에 별도 검증해야 합니다. 완료는 앱의 보고이지 실제 청취 확인이 아닙니다.

## 오류

```json
{"error":{"code":"UNAUTHORIZED","message":"A valid bearer token is required."}}
```

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 필드·좌표·날짜·UUID·페이지 조건 오류 |
| 401 | UNAUTHORIZED | 인증 누락·잘못된 토큰 |
| 403 | SENDER_REQUIRED / RECIPIENT_NOT_ALLOWED | 역할·발신 관계 미허용 |
| 404 | MESSAGE_NOT_FOUND | 없음 또는 본인 수신 메시지가 아님 |
| 409 | IDEMPOTENCY_CONFLICT / EVENT_ID_CONFLICT / VERSION_CONFLICT / INVALID_TRANSITION / NOT_SCHEDULED_TODAY | 중복 키·버전·상태·날짜 충돌 |
| 413 | AUDIO_TOO_LARGE | 파일·요청 크기 초과 |
| 415 | UNSUPPORTED_AUDIO | 형식 미지원·손상 |
| 422 | AUDIO_TOO_LONG | 길이 초과 |
| 503 | STORAGE_UNAVAILABLE / AUDIO_UNAVAILABLE | 저장소·파일 일시 불가 |
| 500 | INTERNAL_ERROR | 내부 처리 실패. 경로·예외를 공개하지 않음 |

## PR에서 협의할 항목

1. 나경·규혁·유림: PCM WAV 또는 다른 코덱, 길이·크기와 웹 녹음 변환.
2. 민석·유림: 실제 로그인·가족/차량 연결과 토큰 발급·회수. 현재 정적 토큰은 로컬 시험 전용.
3. 나경·규혁·유림: recipientId와 가족/차량 연결, 날짜·시간대·만료 정책.
4. 재민·규혁·민석: 실제 SDK와 MOCK 구분, 주차·문·도착·오디오 조건 검증.
5. 전체: 상태 전이·충돌·재시도 UI. 답장·이동 세션·취소·만료·자동 삭제·회원가입은 미구현.


## 연결 가능한 예제

- [팀 연결 순서와 합의 항목](../../services/api/TEAM_INTEGRATION.md)
- [Kotlin 등록·조회·다운로드·재생 결과 통신](../../services/api/examples/kotlin-client/README.md)
- [웹 FormData 등록 함수](../../services/api/examples/web/README.md)

Kotlin 예제의 PC HTTP 시험과 Android 앱의 실제 오디오/SDK 검증은 구분합니다. 기존 API 필드와 상태 전이는 변경하지 않았습니다.
