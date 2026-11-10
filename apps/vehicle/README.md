# 차량 앱 및 Pleos 연동

Android 앱의 개발 언어는 Java·Kotlin입니다. 규혁은 화면과 음성 재생, 재민은 SDK와 차량 상태, 민석은 통합 및 APK 검증을 담당합니다.

두 개발 영역은 하나의 Android 프로젝트를 공유합니다. 모듈 경계와 Gradle 설정 변경을 먼저 조율합니다. 도착·주차 신호의 지원 및 권한을 실환경에서 확인하고, 모의 이벤트는 출처가 드러나도록 기록합니다.

Gradle 프로젝트 생성 시 JDK와 SDK 버전, 빌드 변형, 테스트·Lint·APK 빌드 명령을 이 문서에 기록합니다. [검증 기준](../../docs/verification.md)

## 차마못한말 Android 프로토타입

- Android 프로젝트 위치: `apps/vehicle/android`
- Android Studio에서 해당 폴더를 열고 Gradle 동기화 후 `app`을 실행합니다.
- 개발 중인 기존 패키지명은 `com.example.malttuk`입니다.
- 실행 확인 환경: Pleos Connect v2.1.0, Android API 34 에뮬레이터.
- 서버 조회와 실제 차량 신호는 아직 연결하지 않았습니다.
- 상세 화면의 도착·주차 체크는 개발용 모의 조건이며 실제 차량 상태를 뜻하지 않습니다.

### 로컬 테스트 음성 준비

현재 코드는 `R.raw.chamamothanmal_test` 리소스를 참조합니다.
빌드 전에 개인정보가 없는 테스트용 PCM WAV 파일을 다음 위치에 직접 준비합니다.

`apps/vehicle/android/app/src/main/res/raw/chamamothanmal_test.wav`

해당 파일은 Git에서 제외하며 실제 사용자 녹음을 커밋하지 않습니다.
파일이 없으면 현재 프로젝트는 빌드되지 않습니다.

### 검사 명령

저장소 루트에서 실행합니다.

```powershell
.\apps\vehicle\android\gradlew.bat -p apps/vehicle/android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python scripts/check_repository.py
git diff --check
```

### 수동 확인 범위

Pleos 에뮬레이터에서 개발용 모의 상태로 다음 동작을 확인했습니다.

- 도착·주차·문 닫힘과 이번 이동의 듣기 선택이 모두 충족되면 테스트 음성을 수동으로 재생합니다.
- 상태 미확인, 주행 중, 문 열림에서는 재생을 보류합니다. 재생 중 주행 전환, 문 열림, 듣기 선택 해제, 앱 이탈 시 재생을 중단합니다.
- 끝까지 들은 메시지는 같은 모의 이동에서 다시 재생하지 않습니다. 도중에 중단한 메시지는 조건이 충족되면 수동으로 다시 재생할 수 있습니다.
- 재생 완료 후 주차 상태이고 모의 답장 입력이 허용되면 짧은 답장 버튼을 선택할 수 있습니다. 재생 완료와 답장 선택은 앱 메모리에 보관합니다.

실제 차량 신호, 서버를 통한 답장 전송, 다른 오디오가 재생에 개입하는 상황은 별도 검증이 필요합니다.