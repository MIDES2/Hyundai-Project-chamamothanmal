# 장소 검색과 선택한 위치 전달

서버가 카카오 Local REST API에 검색어를 보내 장소명·주소·위도·경도를 받아 웹에 전달합니다. 전체 지도/주소 DB를 내려받지 않고 검색한 페이지의 결과만 가져옵니다. 지도 타일 표시·사용자의 최종 위치 선택은 웹 담당 영역입니다. 현재 apps/web에는 실제 UI 소스가 없어서 호출 예제를 제공합니다.

## 서버 설정

1. [Kakao Developers](https://developers.kakao.com/)에서 앱의 **REST API 키**를 준비합니다. JavaScript 키·Admin 키와 다릅니다.
2. 저장소 루트의 개인 `.env`에 `KAKAO_REST_API_KEY=실제키`를 추가합니다. 키를 채팅·GitHub·웹 코드·APK에 넣지 않습니다.
3. 기존 서버를 Ctrl+C로 종료하고 services/api에서 scripts/start-local.ps1로 다시 실행합니다.

키가 없으면 서버 전체를 중단하지 않고 장소 검색에만503 PLACE_SEARCH_NOT_CONFIGURED를 반환합니다. 임의 좌표나 가상 장소를 실제 검색 결과로 돌려주지 않습니다. 현재 개발 환경에 키가 없어 실제 카카오 검색은 미검증이며, 외부 응답 계약은 로컬 HTTP 대역으로 시험했습니다.

## 호출

`GET /api/v2/places?query=한신대학교%20정문&type=keyword&page=1&size=10`

Authorization: Bearer 사용자 토큰이 필요합니다. 카카오 키는 클라이언트가 보내지 않습니다. 탈퇴한 앱 사용자도 차단합니다.

| 항목 | 규칙 |
| --- | --- |
| query | 검색어1~100자, 공백만/제어문자 불가 |
| type | keyword=장소명 검색(기본), address=주소 검색 |
| page | 1~45, 기본1 |
| size | 1~15, 기본10 |

응답 예시는 **가상 시험 데이터**입니다. 한신대학교의 실제 주소/좌표가 아닙니다.

```json
{
  "items":[{
    "provider":"KAKAO","id":"fixture-1",
    "placeName":"가상대학교 정문",
    "address":"시험시 가상로 1",
    "roadAddress":"시험시 가상로 1","lotAddress":"시험시 가상동 1",
    "latitude":37.0,"longitude":127.0
  }],
  "type":"keyword","page":1,"size":10,"totalCount":1,"pageableCount":1,"hasNext":false
}
```

카카오의 x는 경도(longitude), y는 위도(latitude)로 변환합니다. address는 도로명 주소를 우선하고 없으면 지번 주소를 사용합니다. 주소 검색은 건물명이 없으면 주소를 placeName으로 사용합니다. 결과가 없으면 items=[]입니다. 정문이 검색되지 않는다고 학교 중심 좌표를 임의로 정문으로 간주하지 않습니다. 사용자가 결과·위치를 확인한 뒤 선택해야 합니다.

다음 페이지는 hasNext=true일 때 page를 증가시킵니다. 검색 버튼 또는 debounce와 AbortController를 사용하고 입력 글자마다 무제한 요청하지 않습니다. 이 기능은 경로 탐색/내비 실행/지도 이미지 제공이 아닙니다.

## 웹 담당자 연결

`examples/web/place-search.js`와 `upload-example.js`를 웹 프로젝트에서 사용합니다.

```javascript
const result = await searchPlaces({ baseUrl, token, query: searchText, type: 'keyword' });
// result.items를 목록에 표시하고, 사용자가 클릭한 항목을 selectedPlace로 보관
const messageId = await registerVoice({
  baseUrl, token, audio, vehicleId, requestId,
  placeName: selectedPlace.placeName,
  address: selectedPlace.address,
  latitude: selectedPlace.latitude,
  longitude: selectedPlace.longitude
});
```

첫 결과를 자동으로 확정하지 말고 이름·주소를 보여준 뒤 선택받습니다. 화면에는 textContent 등 안전한 텍스트 렌더링을 사용합니다. 실제 지도와 검색 목록·녹음 UI는 아직 연결되지 않았습니다.

## 로컬 확인 명령

서버 실행 후 services/api의 다른 터미널에서 실행합니다. 토큰은 로컬 .env에서 읽고 화면에 출력하지 않습니다.

```powershell
.\scripts\search-places.ps1 -Query "한신대학교 정문"
.\scripts\search-places.ps1 -Query "검색할 도로명 주소" -Type address
```

실제 결과는 반환 객체의 items에서 확인합니다. 실제 카카오 키가 필요하며, 키를 넣은 후 반드시 서버를 재시작합니다. 401=사용자 인증,503 PLACE_SEARCH_NOT_CONFIGURED=서버 키 미설정,502 PLACE_PROVIDER_CONFIGURATION_ERROR=키/외부 권한,503 PLACE_SEARCH_LIMITED=외부 제한,504 PLACE_PROVIDER_TIMEOUT=시간 초과입니다. 외부 오류 본문이나 키는 반환하지 않습니다.

## DB 변경

등록 요청에 선택 address(최대500자)를 추가했습니다. 기존 웹·차량에서 생략해도 동작합니다. 선택한 장소 하나의 주소만 메시지와 함께 저장하고 검색 결과 전체/검색 이력은 저장하지 않습니다. local H2는 schema-v2.sql의 ADD COLUMN IF NOT EXISTS로 자료를 보존합니다. Supabase는 배포 전 `supabase/migrations/002_message_address.sql`을 직접 적용해야 합니다.

## 공식 근거

[카카오 Local REST API](https://developers.kakao.com/docs/latest/ko/local/dev-guide): 키워드/주소 검색, REST 인증, 페이지와 x/y 좌표 필드를 기준으로 구현했습니다. 실제 계정의 활성화·키 설정은 해당 프로젝트에서 확인합니다.
