# 최종 기획서 v3 DB 구조

실행 SQL은 src/main/resources/schema-v2.sql, Supabase 수동 적용본은 supabase/migrations/001_vehicle_family.sql입니다. 기존 v1 자료를 삭제하지 않도록 cm_ 접두사로 분리했습니다. local H2에서는 v1·v2를 함께 생성하고 Supabase에서는 비공개 chamamothanmal 스키마에 v2만 생성합니다.

| 테이블 | 주요 내용 |
| --- | --- |
| cm_users | Auth user ID 또는 local demo ID, ACTIVE/DELETED. Supabase 비밀번호는 저장하지 않음 |
| cm_families | 가족 그룹·관리자·활성 여부 |
| cm_members | 그룹 구성원·발신 허용·활성 여부 |
| cm_vehicles | 서버 차량 등록 ID·가족·별칭·활성·캐시 revision |
| cm_codes | 가족 초대/차량 연결 코드 해시,10분 만료,사용 여부 |
| cm_devices | 로그인 사용자와 차량 설치 연결·기기 토큰 해시·활성 |
| cm_messages | 차량·발신자·장소·반경·음성 메타데이터·등록 요청 ID/해시·생성/만료/완료/삭제 시각 |
| cm_sessions | 운전자·연결 기기·차량·선택 목적지·보고 출처·시작/종료·요청 ID/해시. 생성은 주차·수신 동의가 true인 경우만 가능 |
| cm_deliveries | 이동 ID+메시지 ID, 현재 상태와 버전 |
| cm_events | 이벤트 ID·요청 해시·변경 상태·버전·SDK/MOCK/MANUAL·사유·시각 |
| cm_replies | 이동·메시지별 버튼 답장 한 개와 작성자 |
| cm_auth_deletions | 앱 탈퇴 후 Supabase Auth 삭제 재시도 작업 |

음성 자체는 DB가 아닌 로컬 파일 또는 비공개 Storage에 저장합니다. 파일명은 서버 발급 UUID.wav이며 원본 이름·공개 URL을 저장하지 않습니다. family→vehicle→message, vehicle→session→delivery→event/reply로 관계를 연결합니다. 실시간 주행 궤적·VIN·탑승자 자동 식별 정보는 수집하지 않습니다.

## 무결성과 권한

서버는 로그인 ID를 확인하고 사용자 행·가족 행·전달 행 잠금으로 관련 변경을 직렬화합니다. 등록은 UNIQUE(sender_id,request_id), 이동 시작은 UNIQUE(vehicle_id,request_id), 이벤트는 복합 PK로 중복을 막습니다. 연결 코드는 행 잠금 후 한 번만 소모하며 원문을 보관하지 않습니다. 같은 키의 다른 내용은409입니다.

발신자는 자신의 메시지 상세·완료·답장만 확인하고, 차량은 해당 사용자와 연결된 기기 토큰·가족 권한·활성 이동·메시지 유효기간을 모두 확인한 뒤 음성을 받습니다. 사용 제한·주차·도착의 실제 판단은 앱 책임입니다.

## 만료와 삭제

기본24시간 만료는 epoch milliseconds로 저장합니다. 만료/삭제 후 새 파일 접근과 신규 이벤트를 차단합니다. 60초 작업이 파일을 제거하고 실패하면 재시도합니다. 완료 기록은 완료 후30일, 미완료 기록은 만료 후30일 기준으로 이벤트/답장/전달과 함께 제거합니다. 사용하지 않는 오래된 종료 세션·코드도 정리합니다. 권한 철회는 다운로드된 차량 캐시를 물리 삭제하는 기능이 아니므로 앱이 캐시를 지워야 합니다.

탈퇴 시 접근 차단·연결/세션 종료·발신 음성 삭제 처리 후 Auth 삭제 작업을 기록합니다. Supabase Admin 삭제 실패는 재시도하며 최소 user ID 차단 기록은 유지합니다. 실원격 Auth/Storage 삭제는 추가 검증이 필요합니다.

DB와 외부 파일 저장은 하나의 원자 트랜잭션이 아닙니다. DB 등록 실패 시 새 파일을 정리하지만 강제 종료/네트워크 응답 손실의 고아 파일은 후속 대조 정리가 필요합니다. 상용 운영 이전에 마이그레이션·백업·전용 DB 역할을 준비합니다.

## 기존 v1

app_users/send_permissions/messages/playback_events는 local 회귀 시험을 위해 남겼습니다. v2의 차량·이동 구조로 자동 변환하지 않으며 실제 앱은 /api/v2를 사용합니다. 원격 프로필에는 v1 테이블을 만들지 않고 인증 필터가 v1 요청을 차단합니다.
