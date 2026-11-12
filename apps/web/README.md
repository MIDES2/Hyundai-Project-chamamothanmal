# 모바일 웹

나경 담당. 장소·날짜·수신자 선택, 녹음·첨부, 미리 듣기와 등록을 구현합니다.

Java·Kotlin 개발 기준에서 브라우저 화면과 녹음 기능을 어떻게 제공할지 팀에서 정합니다. 프레임워크를 도입하기 전에 대상 모바일 브라우저, 마이크 권한, 음성 형식과 서버 연동을 확인합니다. API 규격과 오류 응답은 유림과 협의합니다.


## 백엔드 장소 검색 연결

2026-11-11 현재 이 폴더에는 실제 화면 소스가 없습니다. 화면을 추가할 때 services/api/examples/web/place-search.js로 GET /api/v2/places를 호출하고, 선택한 placeName,address,latitude,longitude를 upload-example.js의 등록 요청에 연결합니다. 서버가 카카오 REST 키를 보관합니다. 지도 표시·검색 목록·최종 위치 선택·녹음 UI는 웹 담당자가 구현합니다. [장소 검색 안내](../../services/api/PLACE_SEARCH.md)
