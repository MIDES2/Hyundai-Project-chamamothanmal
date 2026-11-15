# 친구 메시지 UI 연동 계약

2026-11-13: 사용자 확인과 최종 기획서의 친구·발신자 청취 모드 기준을 반영했습니다. 아래 경로는 `/api/v2` 기준입니다. 기존 가족·차량·이동 API는 별도로 보존하며 두 메시지 목록을 혼합하지 않습니다.

## 계정과 친구

로컬 개발 서버는 `POST /auth/register`와 `POST /auth/login`을 제공합니다. 가입 필드는 name, handle, phone, password, role(driver/recorder)이며 비밀번호는 8~128자입니다. 아이디는 대소문자 무시, 전화번호는 숫자로 정규화하고 중복을 거부합니다. 전화번호 소유 인증·Pleos OAuth는 제공하지 않습니다. Supabase 인증 모드에서는 로컬 가입·로그인을 제공하지 않습니다.

로그인은 accessToken, expiresAt, profile을 반환합니다. 토큰은 메모리에만 보관하며 8시간 후 또는 서버 재시작/로그아웃 후 무효입니다. 비밀번호는 salt를 사용하는 PBKDF2-SHA256 해시로 저장하고 세션 토큰 원문은 서버에 저장하지 않습니다. 웹 새로고침 후 다시 로그인합니다. 이는 로컬 통합용 인증이며 공개 서비스 인증을 대신하지 않습니다.

- `POST /auth/logout`: 현재 세션 폐기.
- `GET /profile`: 자신의 id/name/handle/phone/role/friendIds.
- `GET /accounts/search?query=...`: 정확한 등록 아이디 또는 전화번호 일치 검색. 전체 사용자 목록·부분 전화번호 검색은 제공하지 않음. 상대 전화번호 원문은 결과에 포함하지 않음.
- `PUT /friends`: `{ "accountId": "상대 ID" }`. 자신·같은 역할은 거부하고 운전자와 녹음자를 양방향 연결. 반복 요청은 중복 생성하지 않음.
- `GET /friends`: 기존 accountId 배열 형식 보존.
- `GET /friends/profiles`: 연결된 활성 계정의 표시 프로필.

## 음성 메시지

- `POST /friend-messages`: multipart의 `message`(application/json)와 `audio`(실제 PCM WAV). UUID `Idempotency-Key`를 사용하고 동일 전송 재시도에서 재사용.
- message 필드: recipientId, placeName, address(선택), latitude, longitude, listeningMode(TOGETHER/PRIVATE), scheduledDate(YYYY-MM-DD, 선택), expiresAt(선택).
- 웹의 자동 재생은 TOGETHER, 재생 전 확인은 PRIVATE로 전송. 이 값은 동승자 감지나 개인 음성 출력 장치를 뜻하지 않음. 차량 스피커 소리는 동승자에게도 들릴 수 있음.
- `scheduledDate`는 한국 시간 수신 시작일. 오늘보다 과거인 날짜는 거부. 오늘은 등록 시점, 미래 날짜는 해당 날짜 00시부터 수신 가능. 기존 클라이언트가 날짜를 생략하면 즉시 수신.
- `availableAt`은 수신 가능 시점, 기본 `expiresAt`은 availableAt+24시간. 직접 지정한 expiresAt도 availableAt보다 뒤여야 함. 예약일 전에는 inbox·음성 다운로드·완료 보고를 허용하지 않음.
- 음성은 15초·5MiB 이하의 PCM WAV(8/16-bit, 8~48kHz, 1~2채널). 웹은 녹음·첨부 음성을 16kHz·모노·16-bit PCM WAV로 실제 변환.

| 호출 | 동작 |
| --- | --- |
| GET /friend-messages/inbox | 수신 가능한 미완료·미만료 메시지 |
| GET /friend-messages/sent | 자신이 보낸 메시지, 완료 상태와 선택형 이모지 반응 |
| GET /friend-messages/{id}/audio | 수신자만 음성 다운로드. 다운로드는 완료 보고가 아님 |
| PUT /friend-messages/{id}/completion | 끝까지 재생한 수신자가 완료 보고. 재시도 시 최초 완료/보관 시각 유지 |
| GET /friend-messages/archive | 완료 후 기본 7일 보관된 수신 메시지 |
| DELETE /friend-messages/{id} | 수신자 삭제. 목록/음성 접근 즉시 차단 |
| PUT /friend-messages/{id}/reaction | 완료한 수신자가 `{ "emoji": "👍" }` 형태로 선택형 답장 |

## 차량 정책

도착·P단·문 닫힘·최신 신호·화면 사용 제한·오디오 포커스가 항상 우선입니다. 미확인 시 보류합니다. 발신자가 PRIVATE이거나 운전자가 항상 확인을 선택하면 확인 후 재생하며, 자동 재생은 TOGETHER이고 운전자 확인 설정이 꺼진 경우만 가능합니다. 플랫폼의 자동 재생 거부 시 수동 듣기를 사용합니다.

문 열림·출발·포커스 상실·화면 이탈 시 중단하고 완료로 기록하지 않습니다. 중단한 음성은 자동 재개하지 않습니다. 새로운 재생은 항상 서버에서 음성을 받아 권한·삭제·만료를 다시 확인합니다. 브라우저 차량 화면은 SDK 미연결인 명시적 MOCK이고 Android는 SDK 또는 디버그의 명시적 MOCK으로 구분합니다.

## 남은 범위

전송 후 수정, 영구 보관·보관 기한 변경, Pleos OAuth, 전화번호 소유 인증은 아직 서버 연동 범위 밖입니다. 실제 SDK/에뮬레이터/스피커/브라우저 녹음 권한 검증은 자동 테스트와 구분합니다. Supabase는 003 다음 004 마이그레이션을 수동 적용해야 하며 이번 작업에서 원격 DB는 변경하지 않습니다.
