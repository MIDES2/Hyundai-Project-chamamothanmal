# 차마못한말 차량 가족 메시지 서버

최종 기획서 v3에 맞춰 **가족이 차량과 목적지에 남긴 음성을, 운전자가 수신을 선택한 이동에서 전달**합니다. Spring Boot·Gradle을 유지하고 Supabase Auth·PostgreSQL·비공개 Storage 연결 모드를 추가했습니다. 기본값은 기존처럼 로컬 H2·가상 계정입니다.

주차·도착·문·오디오·UX 제한은 차량 앱이 확인합니다. 서버가 지원하는 것은 권한·메시지·이동 연결·음성 전달·결과·답장 저장입니다. API의 true/source 보고값은 실제 차량 상태를 증명하지 않습니다.

## 2026-11-11 추가 연결

- 인증된 `GET /api/v2/places`로 카카오 장소명·주소 검색을 지원합니다. REST 키는 개인 .env의 KAKAO_REST_API_KEY로 설정합니다. 현재 실제 키가 없어 원격 검색은 미검증입니다. [장소 검색 안내](PLACE_SEARCH.md)
- 메시지 등록에 선택 address를 추가했습니다. Supabase 사용자는 002_message_address.sql을 먼저 적용합니다. 기존 로컬 데이터는 보존합니다.
- 차량 앱에 별도 서버 연결 화면과 :vehicle-state 판정·실제 파일 다운로드/MediaPlayer·오디오/UX·재생 결과/답장 연결을 추가했습니다. [차량 앱 시험 안내](../../apps/vehicle/android/SERVER_INTEGRATION.md)
- 서버 실행 중 `scripts/prepare-vehicle-demo.ps1`로 합성 소리와 차량 연결 코드를 준비합니다. 실제 사용자·SDK·스피커 시험을 대신하지 않습니다.

## 바로 시작

기존 서버가 실행 중이면 해당 터미널에서 Ctrl+C로 종료한 뒤 다시 켭니다. 기존 .env와 DB·음성 파일을 지우지 않습니다.

```powershell
# 저장소 루트에서
cd services/api
.\scripts\init-local.ps1
.\scripts\start-local.ps1
```

init-local은 루트 .env가 없을 때만 생성합니다. start-local은 JAVA_HOME 또는 설치된 JDK21/Android Studio JBR을 확인해 Gradle bootRun을 실행합니다. Windows 전체 설정을 바꾸지 않습니다. `Started MessageApiApplication`과 http://127.0.0.1:8000/health의 UP이 보이면 정상입니다. 80% EXECUTING에서 계속 머무는 것은 서버 실행 상태입니다.

서버 터미널은 켜두고 새 터미널의 services/api에서 다음을 실행합니다.

```powershell
.\scripts\smoke-test.ps1
# 위 명령과 같은 Kotlin 연결 시험을 직접 실행하려면
.\scripts\make-sample.ps1
.\gradlew.bat :integration-client:run
# 컴파일·자동 시험·JAR 빌드
.\gradlew.bat check bootJar
```

스모크는 합성 WAV로 가족 생성→초대→차량 연결→음성 등록→수신 동의/이동 시작→다운로드→보류/완료 보고→버튼 답장→발신자 확인→연결 해제를 실제 HTTP로 시험합니다. 실제 화면·SDK·스피커 검증은 아닙니다. 새로운 가상 가족·차량 기록이 남습니다. 코드는 토큰·연결 코드를 출력하지 않습니다.

## 기술과 실행 환경

| 항목 | 구성 |
| --- | --- |
| 서버 | Java17 호환 / Spring Boot3.5.7 / JDBC |
| 실행 검증 | JDK21, Gradle Wrapper8.14.3 / Kotlin DSL |
| Kotlin 연결 예제 | Kotlin2.2.21 JVM PC 시험 + Android에 옮길 통신 코드 |
| local 기본 모드 | H2 파일 DB / 비공개 로컬 음성 / demo 계정3개 |
| supabase 프로필 | 온라인 Auth 검증 / PostgreSQL JDBC / private Storage 프록시 |
| 파일 형식 | PCM WAV 8/16-bit·1/2채널·8~48kHz / 최대15초·5MiB |

Gradle 별도 설치 없이 wrapper를 사용하며 최초 다운로드는 인터넷이 필요합니다. 배포 SHA-256과 의존성 lockfile을 고정했습니다. 의도적인 버전 변경 때만 --write-locks로 잠금을 갱신합니다. 한글 Windows 경로의 인자 파일을 위해 JDK21 COMPAT 인코딩과 Kotlin in-process 컴파일을 사용합니다. 소스·JSON은 UTF-8입니다.

## 현재 API와 이전 API

새 앱은 **/api/v2**를 사용합니다. 차량 ID·24시간 유효기간·이동 ID 기반이며 이전 recipientId/scheduledDate와 호환되지 않습니다. 기존 /api/v1은 local 모드의 회귀 시험용으로 남겼고 supabase 인증에서는404로 차단합니다. 이전 시험 명령은 scripts/smoke-v1.ps1입니다.

- [v2 API 명세](../../docs/contracts/backend-api-v0.2.md)
- [조원 연결 순서](TEAM_INTEGRATION.md)
- [DB 구조](DB_SCHEMA.md)
- [Supabase 설정 및 직접 연결로 전환할 때](SUPABASE_SETUP.md)
- [Kotlin 예제](examples/kotlin-client/README.md) / [웹 등록 예제](examples/web/README.md)

## 환경변수

services/api에서 실행하면 application.properties가 루트 ../../.env와 모듈 .env를 읽습니다. 명령행 > OS 환경변수 > 모듈 .env > 루트 .env > 기본값 순입니다. UTF-8 NAME=value, 따옴표/export 없이 Java properties 형식으로 작성합니다. Windows 경로는 C:/...를 사용합니다. 실제 .env는 각자 보관하고 공유 변수 이름만 루트 .env.example에 기록합니다.

| 변수 | 용도 |
| --- | --- |
| APP_ENV | local 시험 인증은 development에서만 허용 |
| API_HOST / API_PORT | 기본127.0.0.1 / 8000 |
| API_DB_URL / API_DB_USER / API_DB_PASSWORD | local H2 연결; 상대 경로는 services/api 기준 |
| API_UPLOAD_DIR | local 저장 폴더 기본 ./uploads |
| API_MAX_AUDIO_BYTES / API_MAX_AUDIO_SECONDS | 최대5242880 / 15 |
| API_CORS_ORIGINS | 기본 localhost·127.0.0.1:5173, 정확한 웹 origin을 쉼표로 구분 |
| API_SENDER_TOKEN / API_RECIPIENT_TOKEN / API_OTHER_RECIPIENT_TOKEN | local demo 계정; 서로 다른 URL-safe32~256자. supabase 서버 인증에서는 사용하지 않음 |
| API_AUTH_MODE / API_STORAGE_MODE | local 기본; 원격은 supabase 프로필 사용 권장 |
| API_CLEANUP_ENABLED | 기본true, 60초 정리 작업 |
| SUPABASE_* | [Supabase 설정](SUPABASE_SETUP.md) 참고. 실제 원격 값은 아직 미설정 |

인증이 없는 API는 /health뿐입니다. 차량 요청은 사용자 토큰 외 X-Vehicle-Token이 필요합니다. 실제 로그인 화면·access_token 갱신은 웹/차량 담당자가 구현합니다. 시험 토큰을 브라우저 빌드 변수나 APK에 넣지 않습니다. 서버 service_role·DB 비밀번호는 앱에 전달하지 않습니다.

## 안전·삭제 규칙

- 관리자가 발급한 10분 일회용 초대로 가족과 차량을 연결합니다. 서버 차량 등록 ID를 사용하고 VIN/탑승자 자동 식별은 사용하지 않습니다.
- 출발 전 주차·공동 청취 동의가 있는 이동만 연결합니다. 서버는 보고값을 기록하며 실제 신호 확인은 앱 책임입니다.
- 초기 범위는 차량당 활성 설치1개, 이동당 메시지1개. 재연결 시 이전 설치·이동을 무효화합니다. 다른 로그인 운전자로 전환할 때 새 연결이 필요합니다.
- 완료 메시지는 다시 자동 전달하지 않습니다. 다운로드 성공은 완료가 아닙니다. 실제 플레이어 콜백으로만 결과를 보냅니다.
- 만료·삭제·연결 해제는 새 접근을 즉시 막습니다. 음성 물리 삭제는 정리 작업에서 최대 다음 주기 이후, 실패 시 재시도합니다. 완료 기록30일, 미완료 만료 후30일 기준 정리. 앱 탈퇴는 접근을 즉시 차단하고 클라우드 Auth 삭제를 비동기로 재시도합니다.
- 차량 자체 캐시는 앱이 정리해야 합니다. 오프라인·권한 확인 실패에서는 새 재생을 보류합니다. 사용자 중지 후 자동 재개하지 않습니다.

## 검증 범위와 남은 작업

로컬 API·Supabase HTTP 계약 대역·Kotlin 통신을 시험합니다. 실제 Supabase 프로젝트, PostgreSQL 원격 SQL 적용, Android UI·SDK·스피커·절전 복원·프로필 변경은 별도 검증입니다. tests는 개인 .env를 읽지 않고 메모리 DB·가상 계정·합성 음성을 사용합니다. schema-v2.sql은 새 구조 생성용이며 운영 스키마 변경 이력을 대신하지 않습니다.

기존 v1 H2·음성은 보존하고 v2로 자동 이관하지 않습니다. local→Supabase도 자동 이관하지 않습니다. 기존 제한된 Windows 실행 환경의 H2 파일 재시작 쓰기 판정 문제가 있었으므로 사용자 터미널에서 재시작 후 새 등록을 확인해야 합니다. DB를 임의 삭제하지 않습니다.

차량 마이크 답장, 탑승자 자동 식별, 다음 운전자 자동 전달, 사진·미션·공개 피드는 초기 구현에 포함하지 않습니다. 실제 성인 계정/아동 보호자 동의 화면, 전체 기기 원격 캐시 삭제·상용 배포 운영 정책은 별도 과제입니다. 서버 중단 시 정리 작업도 멈추고 프로세스 강제 종료 시 고아 파일 정리가 필요할 수 있습니다.

## 공식 참고

[Supabase Auth](https://supabase.com/docs/guides/auth/jwts) · [DB 연결](https://supabase.com/docs/guides/database/connecting-to-postgres) · [Storage 접근 제어](https://supabase.com/docs/guides/storage/security/access-control) · [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
