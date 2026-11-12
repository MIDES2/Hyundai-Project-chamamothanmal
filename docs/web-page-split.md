# 작업 기록: 휴대폰·차량 React 화면별 소스 공유
- 담당 / 날짜 / 브랜치: 프론트엔드 / 2026-11-12 / feature/web-prototype
- 목표: 팀원이 원본 소스를 화면별로 수정하고 실행할 수 있도록 공유한다.
- 수정 영역: apps/web, 루트 README의 원본 진입 안내.
- 관련 계약: API/차량 SDK 계약 변경 없음. Android APK는 별도 작업.
- 게시 대상: origin/feature/web-prototype. main/develop 직접 변경이나 강제 푸시 없이 기존 작업 브랜치에 게시한다.

## 완료한 작업
- 휴대폰 10개 화면과 차량 11개 화면/레이아웃을 각각 파일로 분리했다. 상태·전환은 App.jsx 및 VehicleApp.jsx에 유지했다.
- 공통 달력, 플레이어, 메시지 카드, 버튼, 계정 헤더를 components로 분리했다.
- 기존 AccountPages.jsx는 이전 import 경로 호환용 재내보내기만 유지한다.
- 기존 화면·CSS·차량 SVG를 유지하고 독립 HTML도 원본에서 다시 생성했다.
- 실행 명령, 화면별 경로, 지도 설정, 시연 한계와 APK 미포함을 README에 기록했다.

## 검증 결과
환경: Windows, Node v24.22.1, npm 11.19.1, 설치된 Google Chrome(headless), Playwright. 휴대폰 390×844, 차량 1280×850.
- npm.cmd run build / build:vehicle / lint: 통과.
- npm.cmd run export:mobile / export:vehicle: 통과.
- python scripts/check_repository.py / git diff --check: 통과.
- 브라우저: 가입·로그인, 마이크 권한 거부, 친구 검색·연결, 음성 첨부·등록·수정·취소, 휴대폰 보관함 선택 삭제, 알림 확인.
- 브라우저: 첫 인사·이동·팻말 사전 표시·도착 유지·듣기 선택, 재생 도중 미보관·일시정지/재개·완료 후 자동 보관, 이모지 반응, 보관 기한/삭제, 설정·수신 목록, 포기 시 미보관/삭제 확인.
- 위 브라우저 회귀 검사 모두 통과했고 pageerror가 없었다. Playwright 실행 도구는 로컬 QA 환경이며 프로젝트 런타임 의존성에 추가하지 않았다.

## 남은 작업
실제 인증·기기 간 API 전송·실차 SDK·차량 음성은 연결 전이다. release APK 빌드/서명/설치는 팀 Android 담당 작업이며 이번 검사에 포함되지 않는다. Kakao 실제 키가 없어 실제 지도 API 요청 성공은 검사하지 않았다.

## 2026-11-12: 앱 종류별 폴더 정리
- 휴대폰 원본 전체를 apps/web/src/mobile로 이동했다.
- 차량 브라우저 UI 원본 전체를 apps/web/src/infotainment로 이동했다. Infotainment는 인포테인먼트의 영어 표기이다.
- 공통 Icons.jsx와 원본 SVG는 src/shared로 모았다. HTML 진입점과 모든 상대 import, README의 경로를 함께 수정했다.
- 화면·상태·스타일 동작은 유지했다. build, build:vehicle, lint, 독립 HTML 생성과 기존 Playwright 모바일/차량 회귀 검사가 모두 통과했다.
- 실제 SDK/API/APK 작업 범위는 변경하지 않았다.
