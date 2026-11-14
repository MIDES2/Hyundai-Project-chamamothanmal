# 차마못한말 — 휴대폰·차량 React UI

장소·날짜·상대를 정해 마음을 남기는 휴대폰 화면과, 목적지 도착 후 듣는 차량 화면입니다. 이 폴더는 React 브라우저 프로토타입이며 Android/Pleos 앱 원본은 `apps/vehicle`에서 별도로 개발합니다.

## 2026-11-13 실제 서버 연결

로컬 백엔드를 8000번에서 실행하고 이 폴더에서 `npm.cmd run dev`를 실행합니다. Vite가 `/api`를 로컬 서버로 전달합니다. `/`에서 운전자와 녹음자 계정을 각각 가입하고 로그인한 뒤 정확한 아이디/등록 전화번호로 친구를 연결합니다. `/vehicle.html`은 운전자 계정으로 로그인하는 실제 API 연결 미리보기입니다. 브라우저에는 차량 SDK가 없으므로 차량 조건은 명시적인 MOCK으로만 시험합니다. Android 실제 친구 메시지 화면은 `apps/vehicle/android`에 있습니다.

녹음·첨부 음성은 서버에 맞는 PCM WAV로 변환합니다. 장소는 실제 좌표가 필요하며 지도 키 없이도 장소 이름과 위도·경도를 직접 입력할 수 있습니다. 선택 날짜부터 수신 가능하고 그 시점부터 24시간 뒤 만료됩니다. 재생 완료 음성은 7일 보관합니다. 발신자 재생 전 확인 또는 운전자 항상 확인 중 하나라도 켜져 있으면 확인 후 재생합니다.

계정/친구/전송/목록/보관함/완료/선택형 이모지 답장은 서버와 연결됩니다. 비밀번호·토큰은 브라우저 저장소에 보관하지 않으며 새로고침하면 다시 로그인합니다. 전화번호 소유 인증·Pleos OAuth·전송 후 수정·영구 보관/기간 변경은 제공하지 않습니다. 로컬 개발용 계정이며 공개 서비스 인증은 별도입니다. 기존 단독 HTML은 이전 UI 시연 결과물이고, 새 API 연결은 Vite 또는 `/api`를 같은 출처로 제공하는 배포 서버에서 실행합니다.

## 팀원 실행 방법

저장소를 받은 뒤 이 폴더(`apps/web`)에서 실행합니다. Node.js는 Vite 8이 요구하는 20.19 이상 또는 22.12 이상의 지원 버전을 사용하세요. 검증에 사용한 정확한 버전은 작업 기록을 참고하세요.

```powershell
npm.cmd ci
npm.cmd run dev
```

macOS/Linux에서는 `npm.cmd` 대신 `npm`을 사용합니다. Vite가 출력한 주소(기본 `http://localhost:5173`)로 접속하세요.

- 휴대폰: `/`
- 차량: `/vehicle.html`

`index.html`과 `vehicle.html`은 React 진입 파일이므로 직접 더블클릭하지 않습니다. 기존 `차마못한말_현재.html`, `차마못한말_차량용.html`은 더블클릭 시연용으로 생성한 결과물입니다. **수정은 `src`의 원본에서 하고 HTML은 다시 생성**하세요.

## 화면별 수정 위치

휴대폰 앱은 **[src/mobile](src/mobile/)**, 차량 앱은 **[src/infotainment](src/infotainment/)** 폴더에 있습니다. 두 앱이 함께 쓰는 아이콘·이미지만 `src/shared/`에 둡니다.

| 휴대폰 화면 | 파일 (`src/mobile/pages/`) |
| --- | --- |
| 첫 시작 | `SplashPage.jsx` |
| 로딩 | `LoadingPage.jsx` |
| 로그인 | `LoginPage.jsx` |
| 회원가입 | `SignupPage.jsx` |
| 친구 추가·연결 | `FriendsPage.jsx` |
| 홈: 녹음·첨부·장소·날짜·수신자 | `HomePage.jsx` |
| 꽂아둔말 | `WaitingPage.jsx` |
| 보관함: 선택 삭제 | `ArchivePage.jsx` |
| MY | `MyPage.jsx` |

| 차량 화면 | 파일 (`src/infotainment/pages/`) |
| --- | --- |
| 첫 인사 | `IntroPage.jsx` |
| 도착 후 마음 듣기·포기하기 | `OfferPage.jsx` |
| 새 메시지가 없는 홈 | `EmptyHomePage.jsx` |
| 공통 홈 헤더 및 위 세 화면 배치 | `HomePage.jsx` |
| 차량 이동·도착 | `JourneyPage.jsx` |
| 재생·일시정지 | `PlaybackPage.jsx` |
| 이모지 반응 | `ReactionPage.jsx` |
| 반응 완료 | `DonePage.jsx` |
| 전달받은 마음 목록 | `MessagesPage.jsx` |
| 보관함·보관 기한·삭제 | `ArchivePage.jsx` |
| 설정 | `SettingsPage.jsx` |

이동과 도착은 동일한 SVG 애니메이션을 유지해야 하므로 `JourneyPage.jsx`를 공유합니다. `src/mobile/App.jsx`는 휴대폰 상태·화면 전환, `src/infotainment/VehicleApp.jsx`는 차량 상태·타이머·자동 보관을 관리합니다. 화면 컴포넌트는 props로 상태와 동작을 전달받습니다.

공통 UI는 `src/mobile/components/`(달력·플레이어·메시지 카드·버튼·계정 헤더), 차량 공통 UI는 `src/infotainment/components/`에 있습니다. 스타일은 기존 `src/mobile/styles.css`, `src/infotainment/vehicle.css`를 유지합니다. 원본 차량 SVG는 `src/shared/assets/`에 있습니다.

별도 기능: `PlacePicker.jsx`(장소), `RecipientPicker.jsx`(친구 필터), `Notifications.jsx`(알림), `accounts.js`(데모 계정), `maps/kakao.js`(지도), `infotainment/archive.js`(보관 기한), `infotainment/StatusBar.jsx`(실시간 시계), `infotainment/DockIcon.jsx`(런처 아이콘).

## 필요한 설정과 테스트 환경

- Chrome/Edge 최신 브라우저에서 확인합니다. 마이크 녹음은 HTTPS 또는 localhost와 권한 허용이 필요합니다. 최대 15초이며 권한 거부 시 음성 파일 첨부로 확인할 수 있습니다.
- 지도 검색은 `.env.example`을 `.env.local`로 복사하고 본인의 `VITE_KAKAO_MAP_JS_KEY`를 넣은 뒤 개발 서버를 재시작합니다. 카카오 개발자 콘솔에 접속 도메인을 등록해야 합니다. 공개용 JavaScript 키만 사용하고 REST/Admin 키는 넣지 않습니다. 키가 없어도 나머지 UI와 수동 장소 추가는 확인할 수 있습니다.
- 현재 기본 화면은 실제 로컬 API 연결 화면입니다. 이전 애니메이션 시연 원본은 DemoVehicleApp으로 보존했습니다. 실제 Pleos 인증·차량 SDK는 브라우저에서 제공하지 않습니다.
- 이전 DemoVehicleApp 시연에서만 차량 시작 2초 후 이동, 이동 9초 후 팻말 표시(도착 반 바퀴 전), 13초 후 도착·정지, 2초 후 듣기/포기 선택 화면입니다. 이동 화면 클릭으로 도착을 시연할 수도 있습니다.
- 마음은 끝까지 재생한 뒤에만 자동 보관합니다. 포기하면 해당 마음을 목록과 보관함에서 삭제합니다. 반응은 선택이며 미반응 이유/읽음 여부를 발신자에게 표시하지 않습니다.
- 실제 연결 화면의 계정·친구·음성·목록·완료·반응은 로컬 서버에 저장합니다. 토큰은 메모리에만 유지합니다. 이전 UI 시연 데이터는 새 서버 계정으로 자동 이전하지 않습니다.
- 이전 로컬 UI 시연의 차량 기본 보관 기한은 1주(7일)이며 주/개월/년/영구와 수량을 선택합니다. 이전에 끝까지 들은 마음은 다음 실행에도 보관함에 남습니다.

## 빌드·검사·독립 HTML 생성

```powershell
npm.cmd run lint
npm.cmd run build
npm.cmd run build:vehicle
npm.cmd run export:mobile
npm.cmd run export:vehicle
```

모바일 빌드는 `dist/`, 차량 빌드는 `dist-vehicle/`에 생성됩니다. export 명령은 각각 해당 빌드가 성공한 뒤 실행합니다. 루트에서 `python scripts/check_repository.py`, `git diff --check`로 저장소 규칙도 검사합니다. `node_modules/`, 빌드 폴더, 실제 `.env.local`은 Git에 올리지 않습니다.

## APK 제출

이 React UI 작업에는 release APK가 없습니다. `apps/vehicle`의 Android 빌드·서명·설치 검증 후 제출 안내의 `apk/팀명_앱이름_release.apk` 또는 GitHub Release 링크를 팀에서 준비해야 합니다. HTML을 APK로 이름만 바꿔 제출할 수 없습니다.

## 백엔드 장소 검색 연결

2026-11-12 현재 이 폴더에는 모바일·차량 UI 시연 HTML이 있으며 실제 서버 API 연결은 남아 있습니다. 웹 연동 시 services/api/examples/web/place-search.js로 GET /api/v2/places를 호출하고, 선택한 placeName,address,latitude,longitude를 upload-example.js의 등록 요청에 연결합니다. 서버가 카카오 REST 키를 보관합니다. 지도 표시·검색 목록·최종 위치 선택·녹음 UI는 웹 담당자가 구현합니다. [장소 검색 안내](../../services/api/PLACE_SEARCH.md)

[팀 코드 1차 통합 결과](../../docs/integration-qa-2026-11-12.md)를 참고합니다.

## Pleos APK에 포함되는 화면

`native-vehicle.html` → `src/infotainment/native-main.jsx` → `NativeVehicleApp.jsx`는 Android WebView 전용입니다. `npm.cmd run build:native`는 개인 환경파일을 읽지 않고 `apps/vehicle/android/app/build/generated/vehicleWebAssets/vehicle-ui`로 출력합니다. Android 빌드가 이 작업을 자동 실행하므로 최초 `npm.cmd ci` 후 APK를 빌드하면 됩니다.

이 화면은 브라우저에서 직접 서버를 호출하지 않습니다. `VehicleNative.send`로 로그인·목록·선택·재생·멈춤·답장·삭제를 요청하고 Kotlin이 전달하는 `onVehicleState`만 표시합니다. 토큰·음성 URL·좌표는 화면 상태에 포함하지 않습니다. 브라우저 `/vehicle.html` 미리보기와 별도 진입점이며, 실제 기어·문과 재생 허용 여부는 Kotlin SDK 및 안전 판정이 결정합니다.
