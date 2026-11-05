# 빌드와 검증 기준

| 검사 | 확인하는 내용 | 현재 상태 |
| --- | --- | --- |
| 저장소 기본 검사 | 필수 파일과 로컬 설정 추적 여부 | 실행 가능 |
| Java·Kotlin 컴파일 | 타입 오류, 코드 연결과 의존성 | Gradle 프로젝트 구성 후 |
| JVM 단위 테스트 | 상태 판정, 중복 처리와 서버 인가 | 테스트 구성 후 |
| Android Lint | Android API, 권한 및 리소스 관련 문제 | Android Gradle 구성 후 |
| APK 빌드 및 설치 | 디버그 빌드, 실행과 실환경 동작 | 실행 환경 확보 후 |

Java·Kotlin의 타입 오류는 컴파일에서 확인합니다. mypy는 Python 타입 검사 도구입니다. Kotlin 스타일 검사 도구는 팀이 규칙을 정한 뒤 필요에 따라 적용합니다.

Gradle Wrapper를 추가한 뒤 CI에는 실제 프로젝트에서 확인한 태스크를 등록합니다. Android 앱의 디버그 변형에서는 보통 `testDebugUnitTest`, `lintDebug`, `assembleDebug`를 사용합니다. 서버 검사는 JVM 프로젝트의 `check` 태스크 구성을 확인합니다. Pleos SDK 접근과 실제 차량 검증은 실행 환경과 결과를 따로 기록합니다.
