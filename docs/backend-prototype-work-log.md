# 현재 기준 — Gradle 전환 및 조원 연결

2026-11-08 사용자 요청으로 기존 미커밋 Maven 구성을 Gradle Wrapper 8.14.3 / Kotlin DSL로 전환했습니다. **아래 초기 Maven 검증 기록은 이전 작업 이력**이며 현재 실행은 services/api/README.md를 따릅니다. pom.xml·Maven Wrapper·비강제 의존성 스냅샷을 제거하고 실제 gradle.lockfile로 대체했습니다. 서버 소스는 Java 17 호환, Kotlin 통신 예제는 2.2.21이며 현재 PC JDK21로 검증했습니다.

## 이번 작업 범위

- services/api: Gradle 빌드·배포 SHA-256·의존성 잠금·실행 스크립트, Kotlin 통신 예제, 웹 등록 함수, 팀 연결 안내.
- 공유 문서: 루트 README, 계약 링크, 검증 기준, CI를 실제 Gradle 명령으로 정합화.
- 기존 .env·DB 유지. 공유 환경변수 이름은 기존 .env.example로 충분해 이번 전환에서 추가하지 않음.
- apps/web·apps/vehicle 담당자 코드는 변경하지 않음. 커밋·push 하지 않음.

## Gradle·연결 검증 결과

| 검사 | 결과 | 범위·제한 |
| --- | --- | --- |
| gradlew.bat --no-daemon check bootJar | 성공 | 서버 16 tests, failures0/errors0/skipped0. Kotlin 통신 코드 컴파일. Kotlin 전용 단위 테스트는 없음 |
| 공식 Wrapper 실행 | 성공 | 공식 배포 ZIP SHA-256 검증 후 로컬 캐시에서 실행. 대용량 다운로드 제한 때문에 검증 다운로드만 HTTP Range로 받음; 공유 URL은 공식 배포 주소 유지 |
| Windows 한글 경로 | 인자 파일 오류 수정 후 성공 | JDK21 COMPAT 인코딩 및 Kotlin in-process 컴파일. 소스·HTTP는 UTF-8 |
| Kotlin :integration-client:run | 실제 HTTP 통과 | WAV 등록·동일 요청 재시도·목록·상세·바이트 일치·다른 수신자 차단·MOCK 보류·MANUAL 시작/완료·이벤트 재시도 |
| 웹 등록 함수 | Node에서 실제 HTTP 통과 | FormData 업로드·재시도·한글 장소명 왕복. 브라우저 UI 실행은 아님; CORS 기본 동작은 API 자동 시험에서 확인 |
| start-local.ps1 → Gradle bootRun | 기동 성공 | 시험 포트18082, 별도 메모리 H2로 실행. 기존 로컬 DB 변경 없음 |
| 저장소 검사·git diff --check | 통과 | 원격 CI는 미실행 |

HTTP 시험은 기존 파일 DB와 분리한 메모리 H2를 사용했습니다. 기존 실행 환경의 H2 파일 재시작 쓰기 제한은 아래 기록대로 남아 있으며 사용자 터미널에서 기본 파일 DB의 재시작 후 새 등록을 확인해야 합니다. 실제 Android UI·음성 재생·Pleos 권한·SDK·실차 동작은 미검증입니다.

## 조원에게 전달할 문서

services/api/TEAM_INTEGRATION.md에서 연결 순서·역할·합격 기준을 보고, examples/kotlin-client와 examples/web의 README 및 docs/contracts/backend-api-v0.1.md를 검토합니다. 구현 계약은 팀 합의 전 초안입니다. PCM WAV, 실제 인증, 차량 상태 허용 조건은 조원과 협의해야 합니다.

---

## 초기 구현 이력 (Maven 사용 당시, 현재 실행 명령 아님)

# 작업 기록 메시지 서버 1차 구현

- 담당: 유림 / 구현 지원 Codex
- 날짜: 2026-11-08
- 브랜치: feature/backend-api. 커밋·push·원격 변경은 하지 않음.
- 목표: 가상 음성 등록 → 수신자 조회·다운로드 → 재생 보고 갱신.
- 영역: services/api, API 계약, 루트 환경변수 예시·실행 안내, Git 제외 규칙, CI.

## 선택과 범위

Java 17 호환·Spring Boot 3.5.7·Maven Wrapper 3.9.11·H2 파일 DB를 시제품 선택으로 구현했습니다. 현재 PC의 JDK 21.0.9를 사용합니다. 독립 Maven 설치 없이 wrapper로 실행하며 최초 다운로드가 필요합니다. Maven 배포 체크섬을 고정했습니다. Spring Boot BOM과 명시된 버전으로 의존성을 관리하며 실제 해석 목록은 services/api/dependencies.lock.txt에 기록합니다. Maven에는 Gradle식 내장 잠금이 없으므로 이 파일은 비교용 스냅샷이며 강제 잠금 파일은 아닙니다.

- [x] 메시지·음성 multipart 등록, 목록·상세·비공개 다운로드
- [x] 발신 허용 관계·수신자별 권한 검사, 가상 계정 bearer 토큰
- [x] 파일 크기·실제 PCM WAV·15초 검증, 서버 발급 저장 이름
- [x] 등록 Idempotency-Key, 재생 eventId와 expectedVersion, 상태 전이
- [x] MANUAL/SDK/MOCK 보고 출처 구분. 차량 상태를 서버가 인증하지 않음
- [x] .env 비밀값 로컬 생성·출력 금지, .env.example 공유, 실행·DB·API 문서
- [x] 16개 자동 API 테스트·JAR 빌드, 실제 HTTP 스모크 시험
- [ ] 실제 웹·Kotlin 앱 통합, Pleos SDK·차량 스피커 재생 시험

## 검증

중간 테스트에서 수정한 인증 필터 경로 처리와 시험 업로드 위치를 반영한 뒤 검증했습니다. 실차·UI 검증을 성공으로 표시하지 않습니다.

| 검사 | 실행·환경 | 결과 |
| --- | --- | --- |
| 컴파일·자동 API 시험·JAR 빌드 | JDK21.0.9, Maven3.9.11, mvnw.cmd verify | 16개 통과, 실패0·오류0 |
| Maven Wrapper | 공식 배포 SHA-512 확인 후 ZIP SHA-256 고정 | 다운로드·실행 통과. 이 환경의 임시 폴더 rename 제한 때문에 검증 시 TEMP/TMP만 작업 폴더로 지정 |
| 실제 HTTP | JAR, loopback18080, smoke-test.ps1 | 등록·조회·음성 바이트 일치·다른 수신자 거부·등록/완료 재시도 통과 |
| 디스크 유지 | 같은 DB·파일로 프로세스 재시작 후 조회 | 메시지1건·COMPLETED v2·음성 바이트 일치 |
| 재시작 후 쓰기 | Java NIO 권한 조회 | 실행 환경 제한으로 미검증. Files.isWritable=false이지만 실제 read/write handle은 열리는 불일치로 H2가 읽기 전용 판단. 읽기 검증 때만 spring.sql.init.mode=never 사용. 기본 설정은 유지 |
| 저장소 기본 검사 | python scripts/check_repository.py | 통과 |
| 공백 검사 | git diff --check | 통과 |

사용자 터미널에서 기본 설정으로 시작·새 등록이 되는지는 추가 확인이 필요합니다. 읽기 전용 오류가 나면 DB를 삭제하지 말고 권한·실행 환경을 점검합니다. 테스트 서버는 종료했습니다. CI 설정은 추가했으나 원격 CI·실제 웹·차량 앱·Pleos 시험은 미실행입니다.

## 조원 확인 필요

나경·규혁: PCM WAV 업로드/재생, 브라우저 녹음 변환과 길이·크기. 민석·유림: 시험 토큰을 실제 인증으로 바꾸는 계획. 재민·규혁·민석: 도착·주차·문·오디오 상태와 모의/실제 이벤트 구분. 사용자 ID와 차량 연결·날짜·만료 규칙도 합의가 필요합니다.

## 남은 한계

실제 가족/차량 페어링·토큰 발급/회수·답장·취소·만료·자동 삭제·이동 세션·다른 코덱은 미구현입니다. 로컬 H2·파일 저장과 정적 토큰은 운영 인증·저장소가 아닙니다. 기본 바인딩은 localhost이며 실제 데이터는 사용하지 않습니다. 전원/프로세스 강제 종료 후 고아 파일 정리·DB 마이그레이션·요청 제한도 후속 과제입니다.
