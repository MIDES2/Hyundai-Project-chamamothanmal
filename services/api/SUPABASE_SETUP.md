# Spring Boot를 유지하는 Supabase 연결

현재 구조는 휴대폰/차량 → Spring Boot API → Supabase Auth·PostgreSQL·Storage입니다. 가입·로그인 화면은 조원이 Supabase Auth에 연결하고, 받은 사용자 access_token을 Spring API에 전달합니다. DB 비밀번호와 service_role 키는 서버에만 둡니다. 서버가 사용자·가족·차량 권한을 검사합니다.

## 현재 가능한 것과 미검증 부분

- 코드: Supabase Auth `/auth/v1/user` 온라인 검증, PostgreSQL JDBC 프로필, 비공개 Storage 업로드·다운로드·삭제, 탈퇴 Auth 삭제 재시도 작업을 구현했습니다.
- 로컬: H2·가상 계정으로 같은 /api/v2 흐름을 시험할 수 있습니다.
- Supabase 원격: 프로젝트 정보가 없어 실제 Auth·PostgreSQL·Storage 연결·SQL 적용·재시작 시험은 아직 하지 않았습니다. 원격 연결 성공으로 간주하지 않습니다.
- 기존 H2 메시지·음성은 자동 이관하지 않습니다. 클라우드 전환 시 테스트 계정으로 가족·차량을 새로 연결합니다. 실데이터 이관은 별도 작업입니다.

## 1 프로젝트 준비

팀의 Supabase 테스트 프로젝트를 만들고 다음 값을 확인합니다. 실제 값은 채팅·GitHub에 공유하지 않습니다.

| 로컬 .env 변수 | 확인할 내용 |
| --- | --- |
| SUPABASE_URL | 프로젝트 HTTPS URL |
| SUPABASE_PUBLISHABLE_KEY | publishable 또는 기존 anon 키. 사용자 JWT를 검증하는 요청에 사용 |
| SUPABASE_SERVICE_ROLE_KEY | 서버용 legacy service_role JWT. 이번 REST 어댑터는 이 JWT 방식을 사용; sb_secret 키와 임의로 교체하지 않음 |
| SUPABASE_DB_URL | Connect 안내의 JDBC 연결 문자열, TLS 설정, currentSchema=chamamothanmal |
| SUPABASE_DB_USER / SUPABASE_DB_PASSWORD | 해당 DB 연결의 계정·비밀번호 |
| SUPABASE_STORAGE_BUCKET | voices (다른 이름이면 소문자·숫자·하이픈 3~63자) |

장시간 실행하는 Spring 서버는 직접 연결 또는 session pooler의 값을 검토합니다. IPv4/IPv6 지원을 확인하고 transaction pooler 값을 그대로 사용하지 않습니다. Connect 화면의 호스트·사용자명·포트를 추측하지 마세요. 프로젝트 CA 인증서를 받아 `sslmode=verify-full&sslrootcert=C:/.../인증서.crt`로 서버 인증을 구성합니다. 비밀번호는 URL 대신 별도 변수에 둡니다.

## 2 DB 구조 적용

`supabase/migrations/001_vehicle_family.sql`을 읽고 테스트 프로젝트 SQL Editor에서 실행합니다. 원격 DB에 실제 쓰기가 발생하므로 대상 프로젝트를 확인한 뒤 직접 적용합니다. 서버는 원격 스키마를 자동 생성/삭제하지 않습니다.

- 별도 chamamothanmal 스키마에 cm_* 테이블을 생성합니다.
- 기존 H2 v1 테이블을 삭제하지 않습니다. 로컬의 v2 테이블도 별도로 생성됩니다.
- RLS를 켜고 anon/authenticated의 스키마·테이블 직접 권한을 차단합니다. Exposed schemas에 이 스키마를 추가하지 않습니다.
- JDBC 서버 계정이 스키마와 테이블에 접근할 수 있어야 합니다. 최초 구성은 해당 프로젝트 DB 연결 소유자 계정 기준입니다. 배포 시 전용 최소권한 DB 역할을 별도 검토합니다.
- 현재 브라우저 직접 읽기/쓰기 정책은 제공하지 않습니다. service_role이나 DB 비밀번호를 클라이언트에 넘기는 방식으로 해결하지 않습니다.

## 3 비공개 음성 버킷

Storage에서 voices 버킷을 만들고 **Public을 끕니다**. 파일 제한은 5MiB, audio/wav를 기본으로 설정합니다. 서버가 읽기/업로드 전에 버킷 정보를 확인해 public 버킷을 거부합니다. 객체명은 서버 발급 UUID.wav입니다.

이번 구조에서 앱은 Storage URL로 직접 다운로드하지 않습니다. Spring이 권한·만료·연결을 확인한 후 비공개 객체를 프록시합니다. 사용자용 Storage RLS 쓰기 정책이나 공개/서명 URL은 추가하지 않습니다. service_role은 RLS를 우회할 수 있으므로 서버의 권한 검사가 필수입니다.

## 4 서버 실행

기존 루트 .env에 새 변수만 채웁니다. init-local.ps1은 기존 파일을 수정하지 않습니다. local 모드 설정은 그대로 두고 프로필을 명시적으로 선택합니다.

```powershell
# services/api, JDK21 JAVA_HOME 설정 후
.\gradlew.bat bootRun --args="--spring.profiles.active=supabase"
```

프로필은 Auth·Storage를 supabase 모드로 고정하고, JDBC에 Supabase DB 변수를 사용하며 spring.sql.init.mode=never로 실행합니다. 값이 없으면 시작에 실패합니다. 실제 .env를 커밋하지 않습니다. 로컬 시험은 프로필 없이 기존 start-local.ps1로 실행합니다.

## 5 실제 계정 연결 시험

1. 웹 담당자가 Supabase 가입·로그인으로 성인 시험 계정 두 개를 준비합니다. 비밀번호를 Spring API에 보내는 로그인 API는 없습니다.
2. 각 사용자 access_token으로 `GET /api/v2/me`를 호출합니다. Spring은 Supabase가 확인한 user.id를 사용합니다. JWT 문자열을 단순 해독해 신뢰하지 않습니다.
3. 계정 A로 가족 생성·초대, B로 가입. A로 차량 생성·연결 코드 발급, B로 차량 연결합니다.
4. A로 WAV 등록, B로 주차·공동 청취 동의 후 이동 시작·음성 다운로드·이벤트 보고·버튼 답장을 시험합니다.
5. A가 완료·답장을 조회하고 연결을 해제합니다. B의 다음 조회·재생 전 접근 확인이 차단되는지 봅니다.
6. DB를 재시작해도 관계·기록이 유지되는지, 만료·삭제 시 Storage 파일이 지워지는지, 원격 장애 시 새 재생을 보류하는지 확인합니다.

로컬 demo 토큰은 Supabase 모드에서 허용되지 않습니다. PC Kotlin 스모크를 원격 시험 계정으로 실행하려면 해당 프로세스의 API_SENDER_TOKEN/API_RECIPIENT_TOKEN/API_OTHER_RECIPIENT_TOKEN을 **서로 다른 실제 사용자 access_token**으로 전달해야 합니다. 이는 스모크 클라이언트의 입력 이름일 뿐 서버의 클라우드 인증은 이 정적 토큰을 사용하지 않습니다. 재발급·만료 처리는 실제 로그인 화면 담당자가 구현합니다.

## 6 만료·삭제·탈퇴

24시간 후 새 다운로드/재생 보고는 차단됩니다. 기본 60초 주기의 정리 작업이 만료·삭제된 음성을 제거합니다. 실패 시 다음 주기에 재시도하며, 서버가 꺼져 있으면 정리도 중단됩니다. 완료 기록은 완료 후 30일, 미완료 기록은 만료 후 30일을 기준으로 제거합니다. 앱 탈퇴 시 권한을 즉시 막고 Auth 영구 삭제를 작업 테이블에 남겨 서버 키로 재시도합니다. 실제 Supabase 계정 영구 삭제까지는 원격 검증이 필요합니다. 탈퇴 차단용 최소 user ID 상태 기록은 별도로 남습니다.

차량 앱의 다운로드 캐시는 서버가 원격으로 즉시 지울 수 없습니다. 프로필 변경·연결 해제·401/403/404/410 응답 시 앱이 세션·캐시를 정리해야 합니다. 오프라인에서는 권한 철회 확인이 안 되므로 새 재생을 시작하지 않습니다.

## 나중에 클라이언트가 Supabase에 직접 연결한다면

전환 가능하지만 환경변수만 바꾸는 작업은 아닙니다. 사용자 식별자로 Supabase user.id를 사용하고 관계 테이블을 분리해 이관할 수 있도록 했습니다. 이후 다음 작업이 필요합니다.

- Spring의 가족·차량 권한을 RLS 및 안전한 RPC/Edge Function으로 옮깁니다.
- 연결 코드 소모·이동 동의·이벤트 버전·중복 처리를 서버 측 트랜잭션 함수로 옮깁니다.
- 비공개 Storage 정책, 만료·삭제·Auth 삭제 작업을 별도 서버 작업으로 유지합니다.
- 웹·Kotlin 클라이언트 호출과 계약을 함께 수정하고 기존 데이터/파일을 이관·회귀 시험합니다.

현재 SQL은 직접 접근을 막는 상태입니다. 조원이 직접 연결로 개발 중이면 서비스 키를 전달하지 말고 이 전환 방식을 먼저 합의하세요.

## 공식 자료

- [Supabase JWT와 인증](https://supabase.com/docs/guides/auth/jwts)
- [DB 연결 방식](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [비공개 Storage 접근 제어](https://supabase.com/docs/guides/storage/security/access-control)
- [서버에서 사용자 삭제](https://supabase.com/docs/reference/javascript/auth-admin-deleteuser)
