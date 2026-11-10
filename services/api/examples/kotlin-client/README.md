# Kotlin 차량 가족 API 예제

최종 기획서 v3의 /api/v2 통신 예제입니다. PC 시험과 Android 통신 코드를 제공하며 실제 SDK·UI·플레이어를 구현하지 않습니다.

## PC 시험

서버 실행 후 services/api의 새 터미널에서 다음을 실행합니다. JDK21을 권장합니다.

```powershell
.\scripts\make-sample.ps1
.\gradlew.bat :integration-client:run
# 다른 포트
.\gradlew.bat :integration-client:run --args="http://127.0.0.1:18083"
```

루트/모듈 .env 및 OS 환경변수의 local 시험 토큰을 사용합니다. 가족·차량을 생성하고 코드 연결·등록·수신 동의·이동·파일 일치·권한·보고 재시도·버튼 답장·발신 확인·해제를 검사합니다. 동의/주차/재생 결과는 이 시험에서 MOCK으로 보고하며 실제 사람의 클릭이나 SDK가 아닙니다. 합성 파일 외 실제 녹음은 사용하지 않습니다.

## Android에 가져갈 코드

src/main/kotlin/io/chamamothanmal/client의 **VoiceMessageClient.kt(HTTP/이벤트 타입)**와 **VehicleFamilyClient.kt(v2 API)**를 복사해 검토합니다. SmokeMain.kt와 PC 모듈 build.gradle.kts는 옮기지 않습니다. Android의 org.json을 쓰므로 PC 시험용 org.json 의존성을 중복 추가하지 않습니다. Android Kotlin/Gradle 버전은 해당 프로젝트 기준을 유지합니다.

```kotlin
var vehicleToken: String? = null // 실제 앱에서는 개인 기기 저장소·수명주기 관리
val client = VehicleFamilyClient(
    "http://10.0.2.2:8000",
    { currentUserAccessToken },
    { vehicleToken }
)
// 아래 통신은 Executor 또는 Dispatchers.IO에서 호출. UI 스레드 금지.
val connection = client.pair(codeEnteredByUser)
val vehicleId = connection.getString("vehicleId")
vehicleToken = connection.getString("vehicleToken")
val messages = client.candidates(vehicleId).getJSONArray("items")
```

실제 화면에서 빈 목록·로그인 만료·401/403/404/409/410/503을 처리합니다. 예제는 blocking HTTP·타임아웃·redirect 금지를 사용하며 자동 재시도/자동 재생은 하지 않습니다. 로그인·Supabase token 갱신·암호화 기기 저장·메인 화면은 앱 담당자가 연결합니다.

## 수신 선택과 이동

운전자가 실제 주차 상태에서 이번 이동 수신을 선택한 뒤 다음 함수를 호출합니다. 아래 변수는 실제 UI·확인된 차량 상태에서 가져옵니다.

```kotlin
val started = client.start(vehicleId, requestId, messageId,
    destinationLatitude, destinationLongitude,
    consent = userAccepted, parkedConfirmed = isParked,
    source = "MOCK" // 실제 SDK로 검증한 경우에만 SDK
)
val sessionId = started.getString("sessionId")
val audioBytes = client.download(sessionId, messageId)
```

requestId·본문은 같은 요청 재시도에서 유지합니다. 새 이동에는 새 UUID를 사용합니다. 초기 이동당 메시지 한 개입니다. 실제 신호 상태를 모르면 시작하지 않습니다. 목적지 변경/취소 시 client.end(sessionId,"DESTINATION_CHANGED")와 자체 재생/캐시 정리를 수행합니다.

## 플레이어와 결과 연결

client.session(sessionId)의 deliveries에서 messageId·status·version을 확인합니다. 도착·주차·도어·UX·오디오 조건을 모두 검증하고 client.delivery(sessionId,messageId)로 메시지 권한·만료·이동 유효성을 온라인 확인한 뒤 재생합니다. 응답의 playback이 COMPLETED/SKIPPED이면 재생하지 않습니다. 다운로드 성공을 COMPLETED로 보고하지 않습니다.

```kotlin
val event = VoiceMessageClient.PlaybackEvent(
    eventId = java.util.UUID.randomUUID().toString(),
    status = "HELD", expectedVersion = currentVersion,
    source = "MOCK", reason = "STATE_UNKNOWN"
)
val receipt = client.report(sessionId, messageId, event)
```

실제 시작 콜백 PLAYING, 중단 시 PAUSED/HELD, 완료 콜백 COMPLETED를 순서대로 보냅니다. 응답 version을 다음 새 이벤트에 사용합니다. 타임아웃 재시도는 event 전체를 유지합니다. 409에서는 이동 상세를 다시 조회하고 오래된 응답으로 최신 상태를 덮어쓰지 않습니다. 사용자 중지 후 자동 재개 금지. 권한 확인 실패·오프라인이면 새 재생을 보류합니다.

완료 후 주차 상태에서 client.reply(sessionId,messageId,replyId,"THANK_YOU",true) 또는 ARRIVED_SAFELY를 사용합니다. 초기에는 버튼 답장만 지원합니다. 차량 연결 해제·프로필 변경·권한 오류 시 기기 토큰·세션·음성 캐시를 지웁니다. 서버가 오프라인 캐시를 직접 지우지는 못합니다. 실제 HTTP 허용은 debug에 한정하고 운영은 HTTPS를 사용합니다.

[API v0.2](../../../../docs/contracts/backend-api-v0.2.md) · [팀 연결 순서](../../TEAM_INTEGRATION.md)


## 실제 차량 앱 연결

apps/vehicle/android의 :app이 src/main/kotlin의 공통 통신 두 파일을 소스 경로로 재사용합니다. 복사본을 따로 수정하지 않습니다. PC 전용 SmokeMain.kt는 src/cli/kotlin으로 분리해 APK에 포함하지 않습니다. [차량 서버 연결 화면 안내](../../../../apps/vehicle/android/SERVER_INTEGRATION.md)를 참고하세요. scripts/prepare-vehicle-demo.ps1은 가상 계정 전용이며 1초 합성 소리와 일회용 연결 코드를 준비합니다. 실제 사용자 토큰으로는 이 준비 모드가 실행되지 않습니다.
