# 차량 앱과 서버 연결 시험

기존 MainActivity의 로컬 메시지/오디오 시험은 보존했습니다. 목록 상단의 **서버 메시지 연결** 버튼은 새 ServerIntegrationActivity를 열어 실제 API의 차량 연결·메시지 수신·파일 재생·결과·답장을 사용합니다. :vehicle-state와 공통 Kotlin 클라이언트를 연결했으며 앱/SDK/Gradle 버전은 변경하지 않았습니다.

## 구현 범위

| 연결 | 동작 |
| --- | --- |
| 서버 | 사용자 토큰과 일회용 코드로 차량 연결, 후보 메시지 조회 |
| 수신 선택 | 공동 청취 체크 후 실제 내비 목적지와 메시지로 이동 ID 시작 |
| 음성 | 활성 이동의 파일을 앱 cacheDir에 내려받아 MediaPlayer로 수동 재생 |
| 차량 상태 | VehicleStateMonitor의 기어·도어·GPS·목적지/도착 판정. 준비 후·재생 중에도 재검사 |
| UX·오디오 | CarUxRestrictions 확인 불가/제한 시 보류, 오디오 포커스 상실 시 중단 |
| 결과 | 실제 player.start 후 PLAYING, 완료 콜백에서 COMPLETED, 중단 시 HELD |
| 답장 | 완료 후 주차 상태에서 고마워요/잘 도착했어요 버튼 |
| 재시도 | 같은 eventId·expectedVersion으로 순서대로 재시도. 확인 안 된 결과가 있으면 새 재생 차단 |

자동 재생/백그라운드 서비스는 추가하지 않았습니다. 화면 이탈 시 재생을 멈추고 이동을 종료하며 수신 동의를 초기화합니다. 회전·앱 종료 시 연결 정보는 메모리에서 사라지므로 다시 연결해야 합니다. 실제 프로필 전환·절전 복원·실차 동작은 별도 검증이 필요합니다. 다운로드 파일과 토큰을 영구 보관하지 않습니다.

## 가장 먼저 할 로컬 모의 연동

1. PC services/api에서 scripts/start-local.ps1로 서버를 켭니다.
2. 같은 폴더의 새 터미널에서 다음을 실행합니다.

```powershell
.\scripts\prepare-vehicle-demo.ps1
```

이 명령은 local demo-sender/demo-recipient만 허용하고 가족·차량·1초 합성 소리를 등록합니다. 개인정보/실제 녹음은 사용하지 않습니다. `services/api/build/vehicle-demo-connection.json`에 이번 시험의 서버 주소·시험 사용자 토큰·차량 코드가 만들어집니다. **로컬에서만 열고 공유·커밋하지 않습니다.** 콘솔에는 값을 출력하지 않습니다. 코드는10분 내 한 번만 사용할 수 있고 다시 준비하면 새 가족/차량 기록이 생성됩니다.

3. Android Studio에서 apps/vehicle/android를 열고 app 디버그 빌드를 실행합니다. 기본 프로젝트의 JBR25/Gradle9.6/SDK37 환경을 유지합니다. 서버 터미널의 JDK21과 구분합니다. 에뮬레이터는 별도 실행이 필요합니다.
4. 앱 목록 상단 **서버 메시지 연결**을 누릅니다.
5. **디버그: 모의 P단·도어·도착·UX 조건 사용**을 명시적으로 선택합니다. 실제 신호를 사용한 시험이 아닙니다.
6. JSON 파일의 serverUrlForEmulator·userAccessToken·pairingCode를 화면에 입력하고 **차량 연결**을 누릅니다.
7. **메시지 목록 새로고침** → **모의 시연 목적지** 선택 → 공동 청취 체크 → **이번 이동에서 듣기 시작**을 누릅니다.
8. **도착·주차 확인 후 재생**을 누르면 서버에서 받은 합성 소리가 재생되는지 확인합니다. 기기 음량·오디오 정책 때문에 소리가 나지 않으면 실제 출력은 통과로 표시하지 않습니다.
9. 완료 보고 후 **고마워요**를 누릅니다. 발신자 API 상세에서 답장이 보이는지 확인합니다.
10. 재생 도중 **문 열림 모의/출발 모의**를 눌러 중단을 확인하고, 복구 뒤에도 자동 재생하지 않는지 확인합니다.

HTTP는 debug Manifest에만 허용했습니다. 기본 에뮬레이터에서 PC는 http://10.0.2.2:8000입니다. 실제 기기/다른 PC는 LAN 주소·서버 바인딩·방화벽 설정이 추가로 필요합니다. release는 HTTPS를 사용합니다. 비밀값을 BuildConfig/소스/Gradle properties에 넣지 않습니다.

## SDK 콜백을 연결한 시험

모의 체크를 해제하고 실제 Pleos 콜백·위치 권한을 확인합니다. 출발 전 SDK의 새 P단과 UX 허용, 설정된 내비 목적지를 받은 뒤 수신을 선택합니다. 도착 후 기어 P·도어 닫힘·위치/내비 도착을 VehicleStateMonitor가 판정해야 재생됩니다. 권한이 없거나 값이 오래됐으면 보류합니다. 목적지 변경/취소는 서버 이동을 종료합니다.

MOCK이 하나라도 섞이면 source=MOCK이며 SDK로 바꿔 보고하지 않습니다. 에뮬레이터 콘솔 값이 SDK 콜백으로 들어온 경우에도 실차 시험이 아닌 에뮬레이터임을 기록합니다. 실제 로그인 UI와 Supabase token 갱신은 아직 별도 연결입니다.

## 코드 위치

- app/src/main/java/com/example/malttuk/integration/ServerIntegrationActivity.kt: 새 연동 화면·수동 재생·생명주기
- CarUxGuard.kt: 플랫폼 UX 사용 제한 확인, 실패 시 차단
- PlaybackReporter.kt: 결과 순서·동일 이벤트 재시도·버전 관리
- :vehicle-state: 기존 SDK·GPS 판정 모듈의 구현은 유지
- services/api/examples/kotlin-client/src/main/kotlin: 공통 통신 코드 재사용. PC SmokeMain은 src/cli/kotlin으로 분리

## 검사와 한계

:app/vehicle-state 단위 테스트, Lint, 디버그 APK 빌드를 실행합니다. 에뮬레이터가 실행되지 않은 환경에서는 실제 화면·플레이어 출력·UX 서비스·SDK 콜백을 검증했다고 하지 않습니다. 도구의 제한된 Windows 환경에서는 원본 폴더의 JDK25 쓰기 가능 판정이 잘못되어 같은 소스의 임시 복사본에서 빌드했습니다. 테스트 결과 디렉터리만 작업 폴더로 바꿨으며 원본 위치·코드는 옮기지 않았습니다. SDK API/typo Lint 캐시 생성 경고가 있어 사용자 개발 환경의 Lint도 다시 확인해야 합니다.

한글 경로 빌드 제약과 기본 명령은 상위 README를 따릅니다. 로컬 raw 음성이 없어도 이제 빌드되며 기존 시험 화면에서 안내만 표시합니다. 실제 서버 음성은 APK에 묶지 않습니다.

[서버 실행](../../../services/api/README.md) · [장소 검색](../../../services/api/PLACE_SEARCH.md) · [API 규격](../../../docs/contracts/backend-api-v0.2.md)
