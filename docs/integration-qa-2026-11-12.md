# 팀 코드 1차 통합 검증 — 2026-11-12

## 반영 상태

- QA 변경 PR #8을 develop에 병합했다.
- 검증 기준 커밋은 e7f47b5이며 로컬 develop과 chore/integration-qa에 반영했다.
- 저장소 새 주소는 https://github.com/MIDES2/Hyundai-Project-chamamothanmal 이다.
- 로컬 연습 프로젝트 apps/vehicle/MalTtukVehicle은 커밋에 포함하지 않았다.
- 팀 Android 프로젝트는 apps/vehicle/android이다.

## 계약 변경과 보존 결정

초기 v0.1의 recipientId/scheduledDate 기반 스모크와 단순 수신자 정책은 초기 자료로
보존했다. 현재 서버의 가족·차량·세션 권한을 단순 정책으로 교체하지 않는다.
실제 서버에는 가족/차량 API와 친구 메시지 API가 함께 존재한다.

- 가족·차량 경로 — 공통 VehicleFamilyClient와 차량 ServerIntegrationActivity가 사용한다.
- 친구 메시지 경로 — friend-messages-mvp.md와 FriendMessageController가 제공한다.
- 웹 HTML 2개 — API v1/v2 연결이 없는 UI 시연본이다.
- 통합 대상별 호출 경로를 합의하고 실제 웹·차량 UI에 적용하는 작업이 남아 있다.
  친구 메시지 API에는 차량 토큰·이동 세션이 없으므로 가족 API용 화면에 필드만 바꿔 적용하지 않는다.

## 완료한 검사

| 검사 | 결과 |
| --- | --- |
| 저장소 기본 검사 | 통과 |
| 초기 독립 Java 접근권한 검사 | 16개 통과 |
| 서버 Gradle check / bootJar | 성공 |
| 서버 테스트 | 51개, 실패·오류 0 |
| Kotlin 클라이언트 실 HTTP 연동 | 통과 |
| 친구 메시지 실 HTTP 연동 | 통과 |
| Android 앱 단위 테스트 | 5개, 실패·오류 0 |
| 차량 상태 모듈 단위 테스트 | 46개, 실패·오류 0 |
| Android 앱·상태 모듈 Lint | 통과, 경고는 아래 기록 |
| 디버그 APK | assembleDebug 성공 |

서버 테스트 구성은 FamilyApiTests 16, MessageApiTests 16, PlaceSearchApiTests 5,
PlaceSearchServiceTests 9, SupabaseGatewayTests 5이다. SupabaseGatewayTests는
테스트 더블에 대한 검사이며 실제 원격 Supabase 검증을 의미하지 않는다.

실 HTTP 시험은 임의 시험 토큰, 메모리 H2 DB, 합성 PCM WAV, 로컬 전용 임의 포트를
사용했다. 서버의 .env import를 끄고 원격 프로필과 분리했다. 시험 서버는 종료했다.
기존 사용자 DB·.env·실사용 음성은 변경하지 않았다.

## HTTP 흐름에서 확인한 것

가족 생성 → 초대/가입 → 차량 등록/연결 → 메시지 등록/동일 요청 재시도 →
수신 동의와 이동 시작 → 다운로드 원본 바이트 일치 → 다른 사용자 격리 →
보류/재생/완료 보고와 동일 이벤트 재시도 → 답장 → 발신자 확인 → 차량 연결 해제.

친구 추가 → 친구 음성 등록/동일 요청 재시도 → 수신함 조회 → 다운로드 일치 →
다른 사용자 404·비로그인 401 → 완료/완료 재시도 시각 유지 → 보관함 조회 →
삭제 후 다운로드 404.

MOCK 재생 결과를 API로 보고한 시험이다. 실제 플레이어·SDK·스피커 시험은 미실행이다.
합성 샘플은 services/api/uploads/sample.wav, HTTP 실행 로그는
services/api/build/integration-qa/ 아래에 있으며 Git에서 제외된다.

## 다음 연결 작업

1. 웹에서 친구/수신자 등록에 사용할 최신 계약을 확정하고 UI 시연 상태를 API 호출로 연결한다.
2. 차량의 가족/세션 화면과 친구 수신함 중 실제 시연에 사용할 흐름을 맞춘다.
3. 실행 중인 Pleos가 준비되면 APK 설치·연결·합성 소리 재생·문 열림/출발 중단을 검사한다.
4. UI·SDK·스피커 결과는 합성 HTTP 결과와 구분해 기록한다.
5. 실제 로그인과 원격 장소 검색은 개인 설정이 준비된 환경에서 별도 검증한다.

## 재실행 안내

- 서버 컴파일·시험은 services/api에서 JDK21로 gradlew.bat check bootJar를 실행한다.
- 로컬 서버 실행·현재 스모크 절차는 services/api/README.md를 따른다.
- Android는 apps/vehicle/android에서 JBR25 및 D의 SDK로 testDebugUnitTest, lintDebug,
  assembleDebug를 실행한다.
- 기존 시험 계정·DB를 공유하는 환경에서는 합성 스모크도 새 기록을 생성하므로 테스트 전용 환경을 사용한다.

## Android 최종 검사

앱과 상태 모듈의 단위 테스트·Lint·APK 빌드가 모두 성공했다.
Lint 보고서 집계는 app 27 Warning; vehicle-state 이다.
SDK DoorPositionListener 매개변수 이름에 대한 Kotlin 경고가 남아 있다.
APK는 apps/vehicle/android/app/build/outputs/apk/debug/app-debug.apk에 생성했다.
현재 adb devices에 기기가 없어 APK 설치·화면·SDK·스피커는 미실행이다.
