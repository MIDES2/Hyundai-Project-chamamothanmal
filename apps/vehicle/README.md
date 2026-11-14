# 차량 앱 및 Pleos 연동

Android 앱의 개발 언어는 Java·Kotlin입니다. 규혁은 화면과 음성 재생, 재민은 SDK와 차량 상태, 민석은 통합 및 APK 검증을 담당합니다.

두 개발 영역은 하나의 Android 프로젝트를 공유합니다. 모듈 경계와 Gradle 설정 변경을 먼저 조율합니다. 도착·주차 신호의 지원 및 권한을 실환경에서 확인하고, 모의 이벤트는 출처가 드러나도록 기록합니다.

빌드 환경과 검사 명령은 아래에 기록합니다. [검증 기준](../../docs/verification.md)

## 2026-11-13 실제 친구 메시지 연결

앱 시작 화면은 FriendInboxActivity입니다. 휴대폰 웹에서 가입한 운전자 계정으로 로그인하고 받은 메시지를 조회합니다. 음성 다운로드·완료 보고·7일 보관함·이모지 답장이 친구 메시지 API를 사용합니다. 기존 MainActivity의 로컬 모의 기능과 ServerIntegrationActivity의 가족·이동 API 시험은 디버그 경로로 보존합니다.

기본 서버는 Android 에뮬레이터에서 `http://10.0.2.2:8000`입니다. 디버그에서만 HTTP와 명시적인 MOCK 조건을 허용합니다. 일반 실행은 SDK의 P단·문 닫힘·도착·화면 제한·오디오 포커스를 확인하고 미확인 시 보류합니다. 발신자 확인 모드 또는 운전자 항상 확인 설정 중 하나라도 요구하면 확인 후 재생합니다. 새 APK 설치·실제 SDK 및 스피커 검증은 빌드 성공과 별도로 진행합니다.

## Pleos 내부 차량 디자인 화면

FriendInboxActivity는 APK에 포함한 React 차량 화면을 WebView로 표시합니다. 디자인 원본은 `apps/web/src/infotainment/NativeVehicleApp.jsx`이며 외부 HTML 서버나 Vite 개발 서버가 필요하지 않습니다. 로그인·받은 마음·보관함·재생/멈춤·완료 보고·이모지 답장·삭제는 기존 Kotlin 클라이언트와 MediaPlayer로 처리합니다. 예시 메시지와 가짜 재생 타이머를 사용하지 않습니다.

최초 빌드 전에 `apps/web`에서 `npm.cmd ci`를 실행하고 Node를 사용할 수 있게 합니다. 이후 Android Gradle의 `buildVehicleWebUi`가 화면을 자동 빌드해 APK assets에 포함합니다. 별도 화면 빌드는 `npm.cmd run build:native`이며 개인 `.env.local`을 읽지 않습니다.

화면은 앱에 포함된 자산만 열고 외부 이동·네트워크·파일 접근을 차단합니다. 토큰·음성·좌표를 화면 상태로 전달하지 않으며 로그인 비밀번호는 제출 후 입력란에서 지웁니다. 서버 주소는 에뮬레이터 기준 `http://10.0.2.2:8000`이고 HTTP는 디버그에서만 허용합니다.

설정의 개발·시연용 조건에서 전체 모의 또는 도착만 모의를 선택할 수 있습니다. **기어·문 SDK 시험은 전체 모의를 끄고** 도착만 모의로 선택합니다. 이 경우에도 도착이 모의이므로 화면 출처는 MOCK입니다. 모든 모의를 끄면 SDK 위치·내비를 사용하며 미확인 시 보류합니다. 기존 검증 화면은 디버그 설정에서 열 수 있습니다. 릴리스는 모의 조건을 제공하지 않습니다. 새 디자인의 실제 Pleos 실행·스피커·차량 상태 변경 검증은 빌드 검사와 별도로 진행합니다.

## 차마못한말 Android 프로토타입

- Android 프로젝트 위치: `apps/vehicle/android`
- Android Studio에서 해당 폴더를 열고 Gradle 동기화 후 `app`을 실행합니다.
- 개발 중인 기존 패키지명은 `com.example.malttuk`입니다.
- 실행 확인 환경: Pleos Connect v2.1.0, Android API 34 에뮬레이터.
- MainActivity는 개발용 모의 메시지·차량 상태로 청취 모드, 재생 정책, 이모지 답장과 로컬 보관함을 확인하는 화면입니다.
- 별도 ServerIntegrationActivity는 서버 조회·파일 수신·차량 상태 판정을 사용합니다. 해당 화면의 실제 SDK·서버·스피커 동작은 별도 검증 대상입니다. [서버 연결 안내](android/SERVER_INTEGRATION.md)
- MainActivity의 모의 상태 선택은 실제 차량 신호나 동승자 감지 결과를 뜻하지 않습니다.

### 로컬 테스트 음성 준비

로컬 시험 화면에서 선택적으로 사용할 개인정보 없는 PCM WAV 파일 위치입니다. 파일 없이도 빌드되며, 서버 연결 화면은 이 파일을 사용하지 않습니다.

`apps/vehicle/android/app/src/main/res/raw/chamamothanmal_test.wav`

해당 파일은 Git에서 제외하며 실제 사용자 녹음을 커밋하지 않습니다.
파일이 없으면 로컬 시험 화면에서 안내하고 재생하지 않습니다.

새 모의 메시지를 수신해도 같은 테스트 음성을 사용합니다. 메시지는 서로 다른 ID로 구분하므로 테스트 음성 파일을 여러 개 준비할 필요는 없습니다.

### 청취 모드와 재생 정책

발신자가 지정한 청취 모드를 모의 선택값으로 확인합니다.

| 모드 | 동작 |
| --- | --- |
| 미선택 | 재생 보류 |
| 자동 재생 | 모의 도착·주차·문 닫힘 조건이 충족되면 자동 재생 시도 |
| 재생 전 확인 | 같은 조건이 충족되면 듣기·삭제 확인창 표시 |

- 운전자의 출발 전 ‘이번 이동에서 듣기’ 체크박스는 사용하지 않습니다.
- 주행 중, 문 열림, 도착 전 또는 상태 미확인에서는 새 메시지 재생을 보류합니다.
- 재생 중 조건이 충족되지 않거나 앱을 벗어나면 재생을 중단합니다.
- 오디오 포커스를 잃으면 재생을 중단하도록 처리했습니다. 다른 오디오와의 실제 경합은 별도 검증이 필요합니다.
- 도중 중단은 재생 완료로 기록하지 않습니다. 조건을 다시 충족해도 자동으로 재시작하지 않으며, 수동으로 다시 듣기를 선택합니다.
- 같은 메시지·같은 모의 이동에서 끝까지 재생한 메시지는 중복 재생하지 않습니다.
- 모의 이동·메시지 ID와 중복 방지 상태는 현재 앱 메모리에서 관리합니다. 실제 이동 ID와 서버 메시지 ID 연결은 통합 작업입니다.

### 이모지 답장

음성을 끝까지 들은 뒤 모의 주차 상태이고 모의 답장 입력이 허용되면 이모지를 선택할 수 있습니다.

| 표시 | 의미 |
| --- | --- |
| ❤️ | 하트 |
| 👍 | 좋아요 |
| 😂 | 웃겨요 |
| 😡 | 화나요 |
| 😢 | 슬퍼요 |

- 버튼에는 이모지만 표시하고, 화면 읽기 기능을 위한 한국어 설명을 설정했습니다.
- 모의 답장 입력 상태는 차량 플랫폼의 화면 입력 허용·제한을 시험하기 위한 별도 선택값입니다. 실제 플랫폼 제한 신호와는 아직 연결하지 않았습니다.
- 선택한 이모지는 로컬 보관 메시지의 메타데이터에 반영합니다.
- 서버 답장 전송은 이 모의 화면의 구현 범위에 포함하지 않습니다.
- 음성 인식으로 답장하는 기능은 사용하지 않습니다.

### 로컬 모의 보관함

재생을 끝까지 완료한 테스트 음성을 앱 내부 보관함에 복사해 저장합니다.

- 저장 위치: 앱의 `noBackupFilesDir` 아래 `mock-message-archive`.
- 음성 파일과 메시지 메타데이터를 로컬에 저장합니다.
- 기본 보관 기간은 최초 재생 완료 후 7일입니다.
- 영구 보관을 선택하면 자동 만료 대상에서 제외합니다. 다른 폴더로 이동하는 방식은 아닙니다.
- 보관함의 다시 듣기는 모의 주차·문 닫힘·화면 입력 허용 상태에서 수동으로 실행합니다.
- 보관 음성을 다시 들어도 최초 완료 시각과 보관 만료 시각을 갱신하지 않습니다.
- 보관 음성 재생 중 멈춤, 보관함 닫기 또는 앱 이탈 시 재생을 중단합니다.
- 영구 보관과 삭제는 모의 주차·화면 입력 허용 상태에서, 재생 중이 아닐 때 선택합니다.
- 영구 삭제 버튼을 누르면 확인창을 표시합니다. 취소하면 유지하고, 삭제를 확정하면 로컬 음성과 메타데이터를 삭제합니다.
- 삭제 확정 시에도 현재 화면과 조작 허용 조건을 다시 확인합니다.
- 삭제 확인창에서 앱을 벗어나면 확인창을 닫고, 삭제를 확정하지 않은 메시지는 유지합니다.
- 만료 메시지는 보관함 조회 시 정리하며, 보관함이 열린 동안에는 만료 시각에 목록을 갱신합니다. 앱이 종료된 상태에서 만료 순간에 실행되는 백그라운드 삭제 작업은 없습니다.
- 앱을 종료했다가 다시 실행해도 저장된 보관 메시지는 유지됩니다. 앱 데이터 삭제나 앱 제거 이후까지 보존하는 기능은 아닙니다.
- 서버 보관함 동기화, 사용자·차량 프로필별 분리와 연결 해제 시 정리는 별도 통합 작업입니다.

### 검사 명령

저장소 루트에서 실행합니다.

```powershell
.\apps\vehicle\android\gradlew.bat -p apps/vehicle/android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
py scripts/check_repository.py
git diff --check
```

현재 변경분에서 위 명령의 성공을 확인했습니다.
기존 단위 테스트의 통과는 이번 기능 전체에 대한 자동 검증을 뜻하지 않습니다. 청취 모드·재생 정책·보관함 기능 전용 자동 테스트 추가는 후속 작업입니다.

### 수동 확인 범위

Pleos 에뮬레이터에서 개발용 모의 상태로 다음 동작을 확인했습니다.

- 청취 모드 선택과 출발 전 듣기 체크박스 제거.
- 자동 재생과 재생 전 확인창의 듣기·삭제 처리.
- 미확인·주행·문 열림 상태에서 재생 보류.
- 재생 중 조건 변경과 앱 이탈 시 중단, 조건 복구 후 자동 재시작 방지.
- 같은 모의 이동에서 재생 완료 메시지 중복 재생 방지.
- 재생 완료·주차·입력 허용 조건에 따른 이모지 선택.
- 로컬 보관함 저장, 앱 재실행 후 유지, 영구 보관과 삭제.
- 보관 음성 다시 듣기·중단·보관함 닫기·앱 이탈 처리.
- 보관 음성 다시 듣기 후 최초 완료 시각과 만료 시각 유지.
- 만료 시간을 임시로 1분으로 줄였을 때 임시 보관 메시지는 사라지고 영구 보관 메시지는 유지되는 동작. 확인 후 설정은 7일로 복구했습니다.
- 영구 삭제 확인창의 취소·삭제, 확인창에서 앱 이탈 시 미삭제 유지, 주행·입력 제한 상태의 삭제 버튼 비활성화.

이 결과는 MainActivity의 로컬 모의 기능에 대한 확인입니다. 실제 차량 신호를 연결한 재생 제어, 서버 답장·보관함 동기화, 다른 오디오와의 경합 동작은 별도 검증이 필요합니다.

## 차량 상태 모듈 `:vehicle-state` (재민)

같은 `apps/vehicle/android` 프로젝트의 Android Library 모듈입니다. Pleos Vehicle SDK 기어·도어, Android GPS 위치, Pleos NaviHelper 목적지·변경·취소·도착 이벤트를 받아, 미확인·오래된 상태에서는 재생을 허용하지 않는 판정(`PlaybackGate`)을 제공합니다. 사용법, 판정 규칙, `:app`에 연결할 지점과 에뮬레이터 검증 결과는 [vehicle-state/README.md](android/vehicle-state/README.md)에 있습니다.

- `:app`이 이 모듈에 의존하고 새 ServerIntegrationActivity에서 사용합니다. 기존 MainActivity의 모의 로컬 시험은 유지합니다.
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

위 모듈 검사 명령은 실행 안내입니다. 이번 로컬 모의 기능 변경에서 실행한 검사와 수동 확인 범위는 앞 절에 따로 기록했습니다.

**경로에 한글이 있을 때** (예: `바탕 화면`): AGP가 빌드를 막고, `-Pandroid.overridePathCheck=true`로 넘겨도 단위 테스트가 `ClassNotFoundException`으로 실패합니다. 영문 경로로 옮기거나, 임시 드라이브를 연결해 그 경로에서 실행합니다. junction(`mklink /J`)은 Gradle이 원래 경로로 풀어서 효과가 없습니다.

```powershell
subst Q: "<저장소 경로>"
cd Q:\apps\vehicle\android
# ...검사 실행...
subst Q: /d
```

## (정리됨) 이전 Pleos 연동 검증 앱 — 재민

`apps/vehicle`에 있던 별도 Gradle 루트(기어·Android 위치·NaviHelper 위치 확인 화면)는 기능을 `:vehicle-state`로 옮기고, 2026-11-10 에뮬레이터에서 기어 P/D·도어·GPS·내비 목적지 반영을 모듈로 확인한 뒤 삭제했습니다. 원본은 커밋 `5e5f07f`에서 볼 수 있습니다. 확인 화면을 다시 만들 필요가 있으면 `apps/vehicle/android` 안의 모듈로 추가합니다.