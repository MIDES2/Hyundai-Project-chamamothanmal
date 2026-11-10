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

개발자 로컬 확인: 메시지 상세 진입, 테스트 음성 재생 및 재생 상태 표시.
실제 차량 상태, 서버 연동, 다른 오디오의 개입 동작은 별도 검증이 필요합니다.
생성된 예제 단위 테스트의 통과만으로 재생 정책 검증을 완료했다고 판단하지 않습니다.

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

## (이전) Pleos 연동 검증용 프로젝트 — 재민, 정리 예정

기어·GPS 코드는 `:vehicle-state`로 이전했습니다. NaviHelper 확인 화면을 포함한 테스트 앱 이전은 보류 중이며, 이 중복 Gradle 루트(`apps/vehicle/app`, `apps/vehicle/*.gradle.kts`, `gradlew*`, `gradle/`)는 Virtual Console 기어 변경 반영을 모듈에서 확인한 뒤 삭제합니다. 삭제 후에도 원본은 커밋 `5e5f07f`에서 볼 수 있습니다.

이 폴더가 Gradle 루트입니다. Android Studio에서 `apps/vehicle` 폴더를 엽니다.

| 항목 | 값 |
| --- | --- |
| 모듈 | `:app` (패키지 `com.example.malttuk`, 임시 이름) |
| Gradle / AGP / Kotlin | 9.6.0 (Wrapper) / 9.5.1 / 2.2.10 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 28 |
| Java 호환 | 11 |
| Pleos SDK | `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5` |
| Pleos 저장소 | `https://nexus-playground.pleos.ai/repository/maven-releases/` (settings.gradle.kts) |
| 실행 대상 | Pleos Connect v2.1.0 에뮬레이터 (API 34, x86_64) |

`local.properties`(SDK 경로)는 각자 Android Studio가 생성하며 커밋하지 않습니다.

### 권한

- `pleos.car.permission.CAR_DRIVE`, `pleos.car.permission.NAVI_ROUTE`
- `android.permission.ACCESS_FINE_LOCATION`, `android.permission.ACCESS_COARSE_LOCATION` (실행 중 사용자 허용 필요)

### 테스트 화면 기능

- 기어 조회: `Vehicle.getGear().getCurrentGearSelectionState()` 버튼 조회. 이 API는 SDK 소스에 숨김·지원 중단 표시가 있어 장기 지원을 보장하지 않습니다.
- Android 위치: `LocationManager` GPS 제공자를 1초 간격으로 자동 수신합니다.
- NaviHelper 위치: `getCurrentLocationInfo()` 버튼 조회.

### 검증 상태 (Pleos 에뮬레이터 기준, 실차 미검증)

| 항목 | 결과 |
| --- | --- |
| Android Studio 디버그 빌드·설치·실행 | 성공 |
| 기어 P/D 조회 (Virtual Console로 변경 후 재조회) | 성공 |
| Android 위치 자동 수신, Extended Controls 위치 변경 반영 | 성공 |
| NaviHelper 위치 응답 | 응답은 오지만 위치 변경이 반영되지 않음 |
| 기어 자동 감시, 도착 판정, P단과 도착 결합 | 미구현 |

NaviHelper 위치가 고정되는 원인: 에뮬레이터가 보내는 NMEA 문장을 기본 내비(`ai.umos.maps.android.navigation.app`)가 체크섬 불일치로 모두 거부합니다(`GpsLocationSource: NMEA checksum mismatch`). 같은 에뮬레이터에서 기본 내비 지도 배경이 표시되지 않는 문제도 있으며 원인은 확인되지 않았습니다.

### 빌드·검사 명령

```powershell
cd apps/vehicle
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

위 명령줄 태스크는 아직 실행하지 않았습니다. 지금까지는 Android Studio 실행 버튼으로만 빌드·설치했습니다.
