# 2026-11-11 장소 검색 및 차량 연결 검증

현재 추가 작업은 docs/place-search-vehicle-integration-work-log.md에 기록했습니다. 서버50개·Android51개 단위 테스트, Lint 태스크와 APK 빌드를 통과했습니다. 도구 환경의 임시 복사본/결과 디렉터리 변경과 Lint 캐시 경고를 기록했으며, 실제 카카오 키·에뮬레이터·스피커·Supabase002는 아직 검증하지 않았습니다. 기존 아래 기록은 이전 단계의 이력입니다.

---

# 최종 기획서 v3 검증 기준

2026-11-09 /api/v2 가족·차량·이동·만료·답장 규격을 추가했습니다. 현재 실행·검증은 services/api/README.md와 docs/contracts/backend-api-v0.2.md를 따릅니다. Gradle check bootJar는 v1 회귀·v2 API·Supabase 로컬 HTTP 대역 테스트와 Kotlin 컴파일을 수행합니다. scripts/smoke-test.ps1은 Kotlin v2 실제 HTTP 흐름이며 실제 SDK/재생이 아닙니다. 실제 Supabase PostgreSQL SQL 적용·Auth/Storage·Android는 별도 확인합니다.

---

# 빌드와 검증 기준

## 서버 1차 구현 검사

2026-11-08 서버를 Java 17 호환·Spring Boot·H2·Gradle Wrapper로 구성했습니다. `services/api`에서 `gradlew.bat check bootJar`(Windows) 또는 `sh gradlew --no-daemon check bootJar`로 Java 서버와 Kotlin 통신 예제를 컴파일하고 API 테스트·JAR 빌드를 수행합니다. 테스트는 개인 .env 없이 별도 DB·가상 계정·합성 음성을 사용합니다. GitHub Actions Backend API tests도 Gradle로 변경했습니다. 이 시험은 Pleos SDK·실제 Android 재생 검증이 아닙니다. 서버 실행 후 `gradlew.bat :integration-client:run`은 Kotlin과 실제 HTTP 연결을 검증합니다.

별도 실행 서버의 HTTP 시험은 `scripts/smoke-test.ps1`, 실행·환경변수·한계는 서버 README와 API 계약 문서에 기록합니다. Android 쪽 Gradle·Lint·APK 기준은 아래와 같이 별도로 유지합니다.

| 검사 | 확인하는 내용 | 현재 상태 |
| --- | --- | --- |
| 저장소 기본 검사 | 필수 파일과 로컬 설정 추적 여부 | 실행 가능 |
| Java·Kotlin 컴파일 | 타입 오류, 코드 연결과 의존성 | Gradle 프로젝트 구성 후 |
| JVM 단위 테스트 | 상태 판정, 중복 처리와 서버 인가 | 테스트 구성 후 |
| Android Lint | Android API, 권한 및 리소스 관련 문제 | Android Gradle 구성 후 |
| APK 빌드 및 설치 | 디버그 빌드, 실행과 실환경 동작 | 실행 환경 확보 후 |

Java·Kotlin의 타입 오류는 컴파일에서 확인합니다. mypy는 Python 타입 검사 도구입니다. Kotlin 스타일 검사 도구는 팀이 규칙을 정한 뒤 필요에 따라 적용합니다.

Gradle Wrapper를 추가한 뒤 CI에는 실제 프로젝트에서 확인한 태스크를 등록합니다. Android 앱의 디버그 변형에서는 보통 `testDebugUnitTest`, `lintDebug`, `assembleDebug`를 사용합니다. 서버 검사는 JVM 프로젝트의 `check` 태스크 구성을 확인합니다. Pleos SDK 접근과 실제 차량 검증은 실행 환경과 결과를 따로 기록합니다.
