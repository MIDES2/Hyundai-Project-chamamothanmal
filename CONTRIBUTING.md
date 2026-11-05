# 협업 규칙

## 브랜치
- main: 검증된 제출·시연 기준 버전.
- develop: 팀 개발 통합 기준. 작업 PR은 기본적으로 develop을 대상으로 한다.
- feature/*: 기능 작업. 이번 초기 담당별 브랜치는 README 표를 참고한다.
- fix/*: 버그 수정. chore/*: 설정·CI·정리. docs/*: 문서.
- 초기 담당 브랜치의 PR이 병합된 뒤에는 기능별 새 브랜치를 develop에서 만든다.

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c feature/message-upload
# 작업 및 검사 후, 관련 파일만 add
git add services/api
git commit -m "feat(api): add message upload"
git push -u origin feature/message-upload
```

GitHub에서 작업 브랜치 → develop PR을 연다. 통합 검증 이후 develop → main PR로 반영한다.
공유 브랜치의 강제 push와 이력 재작성은 피한다. 충돌 해결 후 변경 영역을 다시 검사한다.
커밋은 feat, fix, docs, test, chore, ci 접두사를 사용한다.

## 리뷰와 저장소 설정
계약 변경은 연동 담당자의 검토를 받는다. GitHub 아이디를 확인한 뒤 CODEOWNERS를 추가한다.
관리자가 main/develop 보호 규칙에 PR 필수, 승인 1명, `Repository checks` 상태 검사, 강제 push·삭제 제한을 설정한다.
이 문서는 운영 기준이며 서버 측 보호 규칙 자체를 설정하지 않는다.

## 환경·산출물
.env.example만 공유한다. 실제 .env는 각자 생성한다. 비밀값 유출 시 삭제뿐 아니라 해당 키를 폐기·재발급한다.
APK·영상·실제 음성 데이터는 소스 저장소에 넣지 않는다. 제출 APK는 검증 후 GitHub Release 등 팀 합의 공간에서 공유한다.
의존성 잠금 파일과 Gradle wrapper는 프로젝트 생성 시 함께 커밋한다.
