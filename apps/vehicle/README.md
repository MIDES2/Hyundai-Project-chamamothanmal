# 차량 앱 및 Pleos 연동

Android 앱의 개발 언어는 Java·Kotlin입니다. 규혁은 화면과 음성 재생, 재민은 SDK와 차량 상태, 민석은 통합 및 APK 검증을 담당합니다.

두 개발 영역은 하나의 Android 프로젝트를 공유합니다. 모듈 경계와 Gradle 설정 변경을 먼저 조율합니다. 도착·주차 신호의 지원 및 권한을 실환경에서 확인하고, 모의 이벤트는 출처가 드러나도록 기록합니다.

Gradle 프로젝트 생성 시 JDK와 SDK 버전, 빌드 변형, 테스트·Lint·APK 빌드 명령을 이 문서에 기록합니다. [검증 기준](../../docs/verification.md)

## 차마못한말 Android 프로토타입

- Android 프로젝트 위치: `apps/vehicle/android`
- Android Studio에서 해당 폴더를 열고 Gradle 동기화 후 `app`을 실행합니다.
- 개발 중인 기존 패키지명은 `com.example.malttuk`입니다.
- 실행 확인 환경: Pleos Connect v2.1.0, Android API 34 에뮬레이터.
- 서버 조회와 실제 차량 신호는 아직 연결하지 않았습니다.
- 상세 화면의 도착·주차 체크는 개발용 모의 조건이며 실제 차량 상태를 뜻하지 않습니다.

### 로컬 테스트 음성 준비

현재 코드는 `R.raw.chamamothanmal_test` 리소스를 참조합니다.
빌드 전에 개인정보가 없는 테스트용 PCM WAV 파일을 다음 위치에 직접 준비합니다.

`apps/vehicle/android/app/src/main/res/raw/chamamothanmal_test.wav`

해당 파일은 Git에서 제외하며 실제 사용자 녹음을 커밋하지 않습니다.
파일이 없으면 현재 프로젝트는 빌드되지 않습니다.

### 검사 명령

저장소 루트에서 실행합니다.

```powershell
.\apps\vehicle\android\gradlew.bat -p apps/vehicle/android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python scripts/check_repository.py
git diff --check
```

### 수동 확인 범위

Pleos 에뮬레이터에서 메시지 상세 진입과 재생 상태 표시를 확인하고, 개발용 모의 상태로 다음 동작을 검증했습니다.

- 도착·주차·문 닫힘과 이번 이동의 듣기 선택이 모두 충족되면 테스트 음성을 수동으로 재생합니다.
- 상태 미확인, 주행 중, 문 열림에서는 재생을 보류합니다. 재생 중 주행 전환, 문 열림, 듣기 선택 해제, 앱 이탈 시 재생을 중단합니다.
- 끝까지 들은 메시지는 같은 모의 이동에서 다시 재생하지 않습니다. 도중에 중단한 메시지는 조건이 충족되면 수동으로 다시 재생할 수 있습니다.
- 재생 완료 후 주차 상태이고 모의 답장 입력이 허용되면 짧은 답장 버튼을 선택할 수 있습니다. 재생 완료와 답장 선택은 앱 메모리에 보관합니다.

`:app`에서 실제 차량 신호를 받아 재생을 제어하는 동작, 서버를 통한 답장 전송, 다른 오디오가 재생에 개입하는 상황은 별도 검증이 필요합니다. 재생 정책은 위 에뮬레이터 시나리오로 확인했으며, 예제 단위 테스트는 별도로 실행했습니다.

## 차량 상태 모듈 `:vehicle-state` (재민)

같은 `apps/vehicle/android` 프로젝트의 Android Library 모듈입니다. Pleos Vehicle SDK 기어·도어, Android GPS 위치, Pleos NaviHelper 목적지·변경·취소·도착 이벤트를 받아, 미확인·오래된 상태에서는 재생을 허용하지 않는 판정(`PlaybackGate`)을 제공합니다. 사용법, 판정 규칙, `:app`에 연결할 지점과 에뮬레이터 검증 결과는 [vehicle-state/README.md](android/vehicle-state/README.md)에 있습니다.

- `:app`은 아직 이 모듈에 의존하지 않습니다. 연결은 규혁 담당 화면에서 진행합니다.
- 모듈이 Pleos SDK를 쓰므로 `settings.gradle.kts`에 Pleos 저장소(`https://nexus-playground.pleos.ai/repository/maven-releases/`, `ai.pleos.playground` 그룹만)를 추가했습니다.
- 모듈 Manifest가 `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `pleos.car.permission.CAR_DRIVE`·`CAR_DOORS`·`NAVI_ROUTE`를 선언하므로, 연결 후 앱의 병합 Manifest에 함께 들어갑니다.
- 모듈 의존성: `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`.

## 빌드와 검사

| 항목 | 값 |
| --- | --- |
| Gradle / AGP | 9.6.0 (Wrapper) / 9.5.1, Kotlin은 AGP 내장 |
| compileSdk / minSdk | 37 / 28 |
| Java 호환 / Gradle 실행 JDK | 11 / Android Studio 내장 JBR 25 |
| 실행 대상 | Pleos Connect v2.1.0 에뮬레이터 (API 34, x86_64) |

`apps/vehicle/android`에서 실행합니다. Android Studio 밖에서는 `JAVA_HOME`(Android Studio `jbr`)과 `ANDROID_HOME`(Android SDK)을 지정하거나 `local.properties`를 둡니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :vehicle-state:testDebugUnitTest :vehicle-state:lintDebug
.\gradlew.bat :vehicle-state:connectedDebugAndroidTest   # 에뮬레이터 실행 중
```

**경로에 한글이 있을 때** (예: `바탕 화면`): AGP가 빌드를 막고, `-Pandroid.overridePathCheck=true`로 넘겨도 단위 테스트가 `ClassNotFoundException`으로 실패합니다. 영문 경로로 옮기거나, 임시 드라이브를 연결해 그 경로에서 실행합니다. junction(`mklink /J`)은 Gradle이 원래 경로로 풀어서 효과가 없습니다.

```powershell
subst Q: "<저장소 경로>"
cd Q:\apps\vehicle\android
# ...검사 실행...
subst Q: /d
```

## (정리됨) 이전 Pleos 연동 검증 앱 — 재민

`apps/vehicle`에 있던 별도 Gradle 루트(기어·Android 위치·NaviHelper 위치 확인 화면)는 기능을 `:vehicle-state`로 옮기고, 2026-11-10 에뮬레이터에서 기어 P/D·도어·GPS·내비 목적지 반영을 모듈로 확인한 뒤 삭제했습니다. 원본은 커밋 `5e5f07f`에서 볼 수 있습니다. 확인 화면을 다시 만들 필요가 있으면 `apps/vehicle/android` 안의 모듈로 추가합니다.
