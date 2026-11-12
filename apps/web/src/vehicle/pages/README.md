# 차량 화면별 코드

이 폴더의 `.jsx` 파일이 차량용 브라우저 시연 각 화면의 원본입니다.

- 첫 인사·듣기/포기 선택·빈 홈: `IntroPage.jsx`, `OfferPage.jsx`, `EmptyHomePage.jsx`
- 위 화면들의 공통 홈 배치: `HomePage.jsx`
- 차량 이동·도착: `JourneyPage.jsx`
- 재생·반응·완료: `PlaybackPage.jsx`, `ReactionPage.jsx`, `DonePage.jsx`
- 마음 목록·보관함·설정: `MessagesPage.jsx`, `ArchivePage.jsx`, `SettingsPage.jsx`

화면 전환·타이머·자동 보관 상태는 `../VehicleApp.jsx`, 스타일은 `../vehicle.css`에 있습니다.
실행 방법과 실제 차량 연동 범위는 [웹 README](../../../README.md)를 확인하세요. 휴대폰 화면은 [src/pages](../../pages/)에 있습니다.
