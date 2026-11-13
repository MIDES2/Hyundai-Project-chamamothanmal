# 차마못한말 — 휴대폰·차량 React UI

장소·날짜·상대를 정해 마음을 남기는 휴대폰 화면과, 목적지 도착 후 듣는 차량 화면입니다. 이 폴더는 React 브라우저 프로토타입이며 Android/Pleos 앱 원본은 `apps/vehicle`에서 별도로 개발합니다.

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
- 로그인/Pleos 인증, 친구·알림·음성 전송 서버, 차량 SDK/내비게이션, 차량 음성 재생은 연결 전입니다. 차량 파형은 15초 진행을 시연합니다.
- 차량 시작 2초 후 이동, 이동 9초 후 팻말 표시(도착 반 바퀴 전), 13초 후 도착·정지, 2초 후 듣기/포기 선택 화면입니다. 이동 화면 클릭으로 도착을 시연할 수도 있습니다.
- 마음은 끝까지 재생한 뒤에만 자동 보관합니다. 포기하면 해당 마음을 목록과 보관함에서 삭제합니다. 반응은 선택이며 미반응 이유/읽음 여부를 발신자에게 표시하지 않습니다.
- 계정/친구/알림/보관함 메타데이터는 브라우저 localStorage에만 저장합니다. 비밀번호는 메모리에만 있습니다. 등록 음성은 메모리에만 있어 새로고침 시 사라집니다. 실제 개인정보나 실제 음성을 시연에 넣지 마세요.
- 차량 기본 보관 기한은 1주(7일)이며 주/개월/년/영구와 수량을 선택합니다. 이전에 끝까지 들은 마음은 다음 실행에도 보관함에 남습니다.

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
