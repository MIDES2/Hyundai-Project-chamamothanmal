# Hyundai-Project-MalTtuk

말뚝 2.0 — 모바일 웹에서 등록한 음성 메시지를 차량 앱에서 수신·재생하는 팀 프로젝트.

현재 단계: 저장소 기반 구성. 앱 구현, 실제 SDK 지원 및 통합 성공은 아직 검증되지 않았습니다.

## 담당 및 작업 위치

| 담당 | 영역 | 경로 | 초기 브랜치 |
| --- | --- | --- | --- |
| 나경 | React 모바일 웹 | apps/web/ | feature/web-prototype |
| 유림 | FastAPI·DB | services/api/ | feature/backend-api |
| 규혁 | Kotlin 차량 앱·음성 재생 | apps/vehicle/ | feature/vehicle-playback |
| 재민 | Pleos SDK·차량 상태 | apps/vehicle/ | feature/pleos-integration |
| 민석 | 통합·QA·APK·CI | tests/integration/, docs/ | chore/integration-qa |

Pleos 연동과 차량 앱은 같은 Android 프로젝트에서 협업합니다. 모듈 경계와 공통 빌드 파일 변경은 두 담당자가 먼저 조율합니다.

## 시작하기

```powershell
git clone https://github.com/MIDES2/Hyundai-Project-MalTtuk.git
cd Hyundai-Project-MalTtuk
git switch develop
Copy-Item .env.example .env
python scripts/check_repository.py
```

`.env`는 로컬 전용입니다. 예시 변수는 제안이며 앱이 자동으로 읽도록 구현된 상태가 아닙니다. 각 담당자는 프로젝트 생성 후 자기 경로의 README에 설치·실행·테스트 명령과 필요한 버전을 기록합니다. 아직 앱 실행 명령과 APK 빌드 명령은 확정하지 않았습니다.

## 팀 문서

- [협업·브랜치 규칙](CONTRIBUTING.md)
- [에이전트 작업 지침](AGENTS.md)
- [역할·일정](docs/team.md)
- [연동 합의 체크리스트](docs/contracts/README.md)
- [통합 테스트 및 제출물](tests/integration/README.md)
- [작업 기록 양식](docs/work-log-template.md)

CI는 저장소 기본 규칙만 검사합니다. React·FastAPI·Android 테스트 및 빌드는 구현 후 추가합니다.
