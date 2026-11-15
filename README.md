# 차마못한말 (chamamothanmal)

**목적지에 도착해 주차한 순간, 차 안에서 전하는 음성 메시지.**

모바일 웹에서 친구로 연결한 운전자에게 목적지·수신 시작일·청취 모드를 지정해 음성을 보냅니다. 차량 앱은 Android GPS와 Pleos 차량 상태를 확인해 재생하거나 보류하고, 재생 완료 후 보관과 이모지 답장을 지원합니다.

발신자 화면은 **모바일 웹**이며, 제출 APK는 **Pleos 차량용 Android 앱 1개**입니다.

## 제출 APK 및 공개 접속 주소

| 항목 | 주소 / 파일 |
| --- | --- |
| 소스 코드 | [GitHub 저장소](https://github.com/MIDES2/Hyundai-Project-chamamothanmal) · 제출 기준 브랜치 `main` |
| APK 배포 페이지 | [v0.1.0-contest Release](https://github.com/MIDES2/Hyundai-Project-chamamothanmal/releases/tag/v0.1.0-contest) |
| APK 파일 | [Ttotabolka_chamamothanmal_release.apk 다운로드](https://github.com/MIDES2/Hyundai-Project-chamamothanmal/releases/download/v0.1.0-contest/Ttotabolka_chamamothanmal_release.apk) |
| APK 빌드 기준 소스 | [fd3044d464a6421f30bfd5b3968b856cb5a685b3](https://github.com/MIDES2/Hyundai-Project-chamamothanmal/commit/fd3044d464a6421f30bfd5b3968b856cb5a685b3) |
| 모바일 웹 | [차마못한말 열기](https://hyundai-project-chamamothanmal.onrender.com/) |
| 차량 앱에 입력할 서버 주소 | `https://hyundai-project-chamamothanmal.onrender.com` |
| 서버 실행 확인 | [health](https://hyundai-project-chamamothanmal.onrender.com/health) · 정상 응답 `{"status":"UP"}` |

Release APK는 HTTPS 서버에 연결합니다. APK에 서버와 데이터베이스가 포함된 것은 아니므로, 테스트할 때 위 서버가 실행 중이고 인터넷에 연결되어 있어야 합니다. 로컬 개발 시에는 아래의 별도 실행 방법을 사용합니다.

## APK 설치 및 서비스 실행 방법

1. 위 Release에서 APK를 내려받습니다.
2. **Pleos Connect v2.1.0 에뮬레이터**를 실행합니다. 설치된 Android SDK의 `adb`를 사용할 수 있는 터미널에서 다음 명령으로 설치합니다. 경로는 내려받은 APK의 실제 경로로 바꿉니다.

   ```powershell
   adb devices
   adb install -r "C:\다운로드경로\Ttotabolka_chamamothanmal_release.apk"
   ```

3. 에뮬레이터의 앱 목록에서 **차마못한말**을 실행합니다. 위치 권한은 앱 사용 중 허용하고 정확한 위치를 허용합니다.
4. 로그인 화면의 서버 주소 설정에 `https://hyundai-project-chamamothanmal.onrender.com`을 입력합니다.
5. 모바일 웹에서 **운전자 계정**과 **녹음자 계정**을 각각 가입합니다. 등록한 ID 또는 전화번호로 친구를 연결합니다. 테스트용 정보만 사용합니다.
6. 녹음자 계정으로 로그인해 운전자를 수신자로 선택하고, 목적지·수신 시작일·청취 모드를 설정한 뒤 음성을 녹음해 전송합니다.
7. 차량 앱에는 **수신자로 지정한 동일한 운전자 계정**으로 로그인합니다. 받은 마음을 새로고침하고 테스트할 메시지를 선택합니다.
8. 아래의 GPS·주차 조건 테스트를 진행합니다. 재생을 끝까지 마치면 보관함과 이모지 답장을 확인할 수 있습니다.

설치 대상 장치가 여러 개면 `adb -s <장치ID> install -r <APK경로>`로 Pleos 장치를 지정합니다. 앱의 기본 로컬 주소 `http://10.0.2.2:8000`은 개발용이며, 제출 Release 실행 시에는 위 HTTPS 주소로 변경해야 합니다.

## 테스트 환경 및 필요한 설정

### Pleos Connect 에뮬레이터와 화면 규격

| 항목 | 개발·시연 환경 |
| --- | --- |
| Pleos Connect 시스템 이미지 | **v2.1.0** |
| Android | **Android 14 / API 34 / x86_64** |
| 기기 프로필 | Pleos Connect v2.1.0 · 12.8인치 |
| 전체 화면 해상도 | **2560 × 1440 px, 가로 화면** |
| 화면 밀도 | **160 dpi** |
| 개발 시 사용한 AVD 이름 | `2.0 Mallttuk` (사용자 지정 이름) |
| 연동 SDK | Pleos Vehicle **2.3.2**, NaviHelper **3.0.5** |
| 호스트 / 발신 화면 | Windows 11 / Chrome 또는 Edge의 모바일 웹 |

위 해상도는 에뮬레이터 전체 디스플레이 기준입니다. 차량 앱은 시스템의 계기판·상태 표시줄·하단 메뉴를 제외한 앱 영역에 표시됩니다. 일반 Android Automotive 이미지와 Pleos Connect 이미지는 서로 다르므로, SDK 검증에는 Pleos 이미지를 사용합니다. 실차 검증 결과를 의미하지 않습니다.

### 계정·음성·지도 설정

- 모바일 녹음은 HTTPS 또는 localhost에서 마이크 권한을 허용해야 합니다. 최대 **15초·5 MiB**, 서버 지원 형식은 PCM WAV입니다. 웹 녹음은 전송 전에 WAV로 변환합니다.
- 선택 날짜는 **한국 시간 기준 수신 시작일**입니다. 해당 날짜부터 수신 가능하고, 수신 가능 시점부터 **24시간 후 만료**됩니다. 미래 날짜·만료된 메시지는 현재 받은 마음에 표시되지 않습니다.
- 모바일과 차량은 **같은 서버**에 접속해야 합니다. 공개 서버와 개인 PC 로컬 서버는 계정·친구·메시지 데이터가 서로 다릅니다.
- 로컬 지도 설정은 `apps/web/.env.example`을 `apps/web/.env.local`로 복사한 뒤 `VITE_KAKAO_MAP_JS_KEY`에 카카오 **JavaScript 키**를 입력합니다. 카카오 개발자 콘솔에 실제 접속 도메인을 등록하고 Vite를 재시작합니다.
- 브라우저 환경변수에는 REST 키·Admin 키·서버 인증정보를 넣지 않습니다. 서버 장소 검색에 사용하는 `KAKAO_REST_API_KEY`는 서버의 개인 환경설정으로만 관리합니다. 실제 `.env`와 키를 Git에 올리지 않습니다.

### GPS 도착·주차 및 재생 조건 테스트

1. 수신 시작일이 현재 유효한 새 메시지를 보내고, 차량의 **받은 마음에서 해당 메시지를 먼저 선택**합니다. 현재 구현은 선택한 메시지의 목적지 조건을 감시합니다.
2. 차량 설정에서 **항상 확인 후 재생**을 끕니다. 자동 재생 테스트 메시지는 **함께 듣기**로 보냅니다. **혼자 듣기** 메시지는 항상 확인을 요구하며, 운전자 설정이 확인 모드이면 함께 듣기도 확인을 요구합니다.
3. 디버그 앱의 개발·시연용 설정이 있다면 전체 모의와 도착 모의를 모두 끄고 SDK 실제 사용을 선택합니다. 화면 출처가 `SDK`인지 확인합니다. 앱이 만든 `MOCK` 조건은 SDK 검증과 구별합니다.
4. 에뮬레이터 도구 모음의 **⋮ → Extended Controls → Location → Single points**에서 목적지와 떨어진 위치를 선택하고 **Set Location**을 누릅니다. 지도에서 위치를 찾기만 하는 것으로는 GPS가 적용되지 않습니다.
5. Pleos 차량 상태 조작 도구에서 기어를 **P**, 지원되는 문 상태를 **모두 닫힘**으로 설정합니다. 이때 목적지 밖에서는 재생이 보류되는지 확인합니다.
6. Location에서 메시지의 목적지 좌표를 선택하고 **Set Location**을 누릅니다. Android GPS가 목적지 반경 안으로 갱신되고 주차·문·화면·오디오 조건이 충족되면 자동 재생을 시도합니다. 위치는 최근 **15초 이내**, 정확도 **100 m 이하**여야 하며 목적지 반경 기본값은 **100 m**입니다. 내비 목적지가 설정되어 있다면 메시지 목적지와 일치해야 합니다.
7. D단·문 열림·도착 전·상태 미확인에서는 재생이 보류되는지 확인합니다. 재생 중 조건이 깨지거나 앱이 화면에서 사라지면 중단하며, 도중 중단은 완료로 처리하지 않습니다. 중단 후에는 조건을 회복하고 수동 재생을 선택합니다.
8. 음성을 끝까지 들은 뒤 완료 보고, **7일 보관함**, 이모지 답장을 확인합니다.

이 테스트는 **에뮬레이터에 주입한 Android GPS와 Pleos SDK 신호**를 이용합니다. 실차 주행 검증은 아니며, 브라우저의 `/vehicle.html`은 MOCK 미리보기이므로 Pleos SDK 테스트를 대신하지 않습니다. 자동 재생은 앱이 화면에 표시된 상태에서 확인하며, 모든 메시지의 자동 선택이나 백그라운드 재생을 지원하는 것은 아닙니다.

## 소스에서 실행하기

### 준비 도구

| 영역 | 필요한 도구 및 프로젝트 설정 |
| --- | --- |
| 모바일 웹 | Node.js **20.19+ 또는 22.12+** / npm · React 19 / Vite 8 |
| 서버 | **JDK 21** 실행 확인 / Spring Boot 3.5.7 / Gradle Wrapper 8.14.3 |
| 차량 앱 | Android Studio / **JDK·JBR 25** / Android SDK 37 / Gradle Wrapper 9.6.0 / AGP 9.5.1 |
| 실제 차량 UI 테스트 | 위 표의 Pleos Connect 에뮬레이터 / Android SDK Platform-Tools (`adb`) |
| 저장소 검사 | Python 3 |

서버와 Android 프로젝트는 Gradle·JDK 요구사항이 다릅니다. 서버 터미널의 `JAVA_HOME`은 JDK 21로, Android Studio의 Gradle JDK는 JDK·JBR 25로 지정합니다. Android의 **빌드 SDK 37**과 **실행 이미지 API 34**는 서로 다른 설정입니다.

```powershell
git clone https://github.com/MIDES2/Hyundai-Project-chamamothanmal.git
cd Hyundai-Project-chamamothanmal
git switch main
```

### 1. 백엔드 서버 — 터미널 1

저장소 루트에서 실행합니다. JDK 21을 사용할 수 있게 설정한 뒤 진행합니다.

```powershell
cd services/api
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\init-local.ps1
.\gradlew.bat bootRun
```

`init-local.ps1`은 루트 `.env`가 없을 때만 필요한 로컬 인증값을 생성하며 기존 파일을 덮어쓰지 않습니다. 위 실행 정책 옵션은 해당 PowerShell 프로세스에만 적용합니다. 기존 `.env`의 필수 설정이 비어 있으면 `.env.example`과 비교해 채웁니다.

`Started MessageApiApplication`과 [로컬 health](http://127.0.0.1:8000/health)의 `UP` 응답을 확인합니다. 서버는 계속 실행되는 프로그램이므로 `80% EXECUTING / :bootRun` 상태에서 터미널을 유지하는 것이 정상입니다. 기본 저장소는 로컬 H2와 로컬 음성 파일이며, Supabase 연결은 선택 사항입니다.

### 2. 모바일 웹 — 터미널 2

새 터미널의 저장소 루트에서 실행합니다.

```powershell
cd apps/web
npm.cmd ci
if (-not (Test-Path .env.local)) { Copy-Item .env.example .env.local }
npm.cmd run dev
```

Vite가 출력한 브라우저 주소로 접속합니다. 기본값은 `http://localhost:5173`이며 포트가 사용 중이면 달라질 수 있습니다. 개발 서버는 `/api` 요청을 `http://127.0.0.1:8000`으로 전달합니다. 지도 키는 앞의 설정을 따릅니다.

### 3. Pleos 차량 앱 — Android Studio

1. 위 `npm.cmd ci`를 먼저 완료하고, Node를 사용할 수 있는 상태에서 Android Studio를 실행합니다.
2. **`apps/vehicle/android` 폴더**를 프로젝트로 엽니다. 별도 연습 프로젝트인 `MalTtukVehicle`을 열지 않습니다.
3. Gradle JDK와 Android SDK 경로를 설정하고 동기화를 완료합니다.
4. Pleos 에뮬레이터를 실행하고 실행 장치로 선택한 뒤, 실행 항목 `app`의 **▶ Run**을 누릅니다.
5. 로컬 개발용 debug 앱은 서버 주소 `http://10.0.2.2:8000`을 사용합니다. 이는 에뮬레이터에서 호스트 PC의 서버에 접근하는 주소입니다. 공개 서버로 테스트할 때는 앞의 HTTPS 주소로 바꿉니다.

Gradle은 React 차량 화면을 빌드해 APK 안에 포함합니다. **차량 화면을 띄우기 위해 별도의 Vite 서버를 켤 필요는 없습니다.** Kotlin이 서버 통신·SDK·음성 재생을 담당합니다. 상세 설명은 [차량 앱 README](apps/vehicle/README.md)를 참고하세요.

## 저장소 구조와 담당

| 경로 | 역할 / 담당 |
| --- | --- |
| `apps/web/src/mobile/` | React 모바일 웹·녹음·발신 화면 / 나경 |
| `apps/web/src/infotainment/` | 차량 React UI, APK 전용 `NativeVehicleApp.jsx` |
| `services/api/` | Java·Spring Boot API, 계정·친구·음성·결과 저장 / 유림 |
| `apps/vehicle/android/app/` | Kotlin 차량 화면 연결·음성 수신 및 재생 / 규혁 |
| `apps/vehicle/android/vehicle-state/` | Pleos SDK·Android GPS·상태 판정 / 재민 |
| `tests/integration/`, `.github/`, `scripts/` | 통합·검사·빌드 및 제출 연동 / 민석 |

팀 개발 통합은 `develop`, 제출·시연 기준은 `main`으로 관리합니다. 변경은 작업 브랜치 → `develop` PR → `main` PR 순서로 반영합니다. [협업 규칙](CONTRIBUTING.md)

## 검사 명령 및 관련 문서

저장소 루트에서 `python scripts/check_repository.py`, `git diff --check`를 실행합니다. 영역별 검사는 아래와 같습니다.

| 실행 위치 | 명령 |
| --- | --- |
| `apps/web` | `npm.cmd run lint`, `npm.cmd run build`, `npm.cmd run build:native` |
| `services/api` | `.\gradlew.bat check bootJar` |
| `apps/vehicle/android` | `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` |

컴파일·자동 시험·APK 빌드 성공과 에뮬레이터에서의 로그인·GPS·SDK·스피커 검증은 구별합니다. 저장소 기본 검사는 파일·환경설정 추적 규칙을 확인하며 앱 동작과 보안 전체를 보증하지 않습니다. 실제 음성·개인 환경설정·서명 키는 커밋하지 않습니다.

- [모바일 웹 실행 및 UI 구조](apps/web/README.md)
- [차량 앱과 Pleos 연동](apps/vehicle/README.md)
- [서버 실행 및 환경설정](services/api/README.md)
- [현재 친구 메시지 API 계약](docs/contracts/friend-messages-mvp.md) — 새 웹·차량 UI 연동 기준
- [차량 신호 및 기존 API 계약 문서](docs/contracts/README.md)
- [통합 테스트 시나리오](tests/integration/README.md)
- [보안과 데이터 처리](docs/security.md)
- [빌드 및 검증 기준](docs/verification.md)
