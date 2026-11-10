# 모바일 웹 v2 등록 예제

upload-example.js는 최종 기획서의 차량 대상 메시지 등록 함수입니다. 전체 녹음 UI·로그인 화면은 포함하지 않습니다. 기존 웹 개발 서버에서 가져다 사용하고 파일을 더블클릭해 실행하지 않습니다.

```javascript
import { registerVoice } from './upload-example.js';
const requestId = crypto.randomUUID(); // 같은 재시도에서는 유지
const messageId = await registerVoice({
  baseUrl: 'http://127.0.0.1:8000',
  token: currentUserAccessToken,
  audio: fileInput.files[0], // 실제 PCM WAV
  vehicleId: selectedVehicleId,
  placeName: '가상 시험 장소', latitude: 37.0, longitude: 127.0,
  expiresInHours: 24, radiusMeters: 100, requestId
});
```

currentUserAccessToken·selectedVehicleId·fileInput은 실제 화면에서 가져옵니다. Supabase 모드에서는 로그인 access_token이며 local은 입력받은 시험 발신 토큰입니다. 사용자 토큰을 프런트 빌드 변수·소스에 넣지 않습니다. DB 비밀번호와 service_role은 절대 전달하지 않습니다.

등록 전에 GET /api/v2/me로 가족을 확인하고 GET /api/v2/families/{id}/vehicles로 대상 차량을 선택합니다. 관리자 가족 생성·초대·차량 등록은 API v0.2를 따릅니다. 실제 가입·로그인과 토큰 갱신은 Supabase Auth UI에 연결해야 합니다.

등록 후 GET /api/v2/messages/sent 또는 /messages/{messageId}로 본인 메시지의 완료·버튼 답장을 확인합니다. 삭제는 DELETE /messages/{messageId}입니다. 과거 recipientId·scheduledDate 방식은 사용하지 않습니다. expiry 응답은 UTC epoch milliseconds입니다.

PCM WAV 최대15초·5MiB만 지원합니다. WebM/MP3/M4A의 이름만 WAV로 바꾸지 않습니다. 브라우저 녹음 변환/코덱 확대를 차량 담당자와 협의하세요. 파일명은 서버 저장 경로로 쓰지 않습니다. 브라우저가 FormData boundary를 설정하므로 Content-Type을 직접 넣지 않습니다.

기본 CORS는 localhost/127.0.0.1:5173입니다. 주소가 다르면 API_CORS_ORIGINS에 정확한 웹 origin을 추가하고 서버를 재시작합니다. 다른 PC/휴대폰은 서버의 LAN 주소·바인딩·방화벽을 확인해야 합니다. 이 예제는 UI·브라우저 권한/녹음 동작 검증을 대신하지 않습니다.

[API v0.2](../../../../docs/contracts/backend-api-v0.2.md)


## 장소 검색 추가

`place-search.js`의 searchPlaces({baseUrl,token,query,type,page,size,signal})는 서버 /api/v2/places를 호출합니다. type은 keyword 또는 address입니다. 결과의 items를 목록에 표시하고 사용자가 선택한 placeName,address,latitude,longitude를 registerVoice에 함께 전달합니다. 카카오 키는 웹에 넣지 않습니다. [상세 안내](../../PLACE_SEARCH.md)
