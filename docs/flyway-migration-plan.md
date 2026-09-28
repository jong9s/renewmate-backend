# Flyway 마이그레이션 계획

## 적용 범위

로컬과 운영 환경의 데이터베이스 스키마 변경은 Flyway가 관리합니다. Hibernate는 더 이상 스키마를 자동으로 변경하지 않고, Flyway 적용 결과가 JPA Entity와 일치하는지만 검증합니다.

| 버전 | 목적 |
| --- | --- |
| V1 | 사용자, 카테고리, 구독, 알림, 사용자 설정 기본 테이블 생성 |
| V2 | Google 식별자와 일회용 OAuth 교환 코드 추가 |
| V3 | 일회용 비밀번호 재설정 토큰 추가 |

카테고리 기준 데이터는 스키마가 아닌 운영 데이터이므로 이번 마이그레이션에서 임의로 생성하거나 변경하지 않습니다.

## 기존 데이터베이스에 Flyway 도입

현재 배포 데이터베이스에는 기본 스키마와 Google OAuth 스키마가 이미 존재합니다. 따라서 로컬과 운영 프로필은 다음 설정을 공통으로 사용합니다.

```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=2
spring.jpa.hibernate.ddl-auto=validate
```

`flyway_schema_history`가 없는 기존 DB에 Flyway를 처음 적용하면 현재 구조를 V2까지 적용된 것으로 기록합니다. 이후 V3부터 순서대로 실행합니다.

이전 `ddl-auto=update` 설정으로 `password_reset_tokens`가 이미 생성됐을 가능성이 있어 V3는 `CREATE TABLE IF NOT EXISTS`를 사용합니다. 단, 필요한 컬럼 구조가 Entity와 다르면 Hibernate 검증 단계에서 애플리케이션 시작이 실패합니다.

## 로컬 검증 결과

검증일은 2026-09-28이며 로컬 MySQL 8.0.46과 검증 전용 임시 스키마만 사용했습니다. 운영 DB와 Docker named volume은 변경하지 않았습니다.

### 비어 있는 신규 DB

- 임시 스키마: `renewmate_flyway_empty` (검증 후 삭제)
- Flyway 결과: V1, V2, V3 적용 성공
- 확인 결과: 애플리케이션 테이블 7개 생성
- Hibernate 결과: 스키마 검증 통과
- Actuator 결과: `UP`

### V2까지 존재하는 기존 DB

- 임시 스키마: `renewmate_flyway_existing` (검증 후 삭제)
- 초기 상태: V1·V2 구조 존재, Flyway 이력과 비밀번호 재설정 테이블 없음
- Flyway 결과: V2 baseline 기록 후 V3 적용 성공
- 확인 결과: `password_reset_tokens` 생성
- Hibernate 결과: 스키마 검증 통과
- Actuator 결과: `UP`

### 자동 테스트

`gradlew clean test`가 성공했습니다. H2를 사용하는 테스트 프로필에서는 Flyway를 끄고 Hibernate `create-drop`을 유지해, MySQL 전용 SQL과 단위·통합 테스트 환경을 분리했습니다.

## 운영 반영 전 확인 사항

이번 작업에서는 운영 DB와 배포 환경에 Flyway를 적용하지 않았습니다.

1. 운영 스키마를 백업하고 실제 복구 가능 여부를 확인합니다.
2. `users.google_subject`와 `oauth_exchange_codes`를 포함해 V1·V2 구조가 존재하는지 확인합니다.
3. `password_reset_tokens` 존재 여부와 컬럼·제약조건을 확인합니다.
4. 운영 DB 복제본에서 Flyway와 Hibernate 검증을 먼저 실행합니다.
5. 점검 시간에 배포하고 `flyway_schema_history`, 애플리케이션 시작 로그, `/actuator/health`를 확인합니다.
6. `flyway clean`, MySQL named volume 삭제, Flyway 이력 수동 변경은 수행하지 않습니다.

## 롤백 원칙

버전 마이그레이션은 이전 버전으로 자동 복구하지 않고 앞으로만 적용합니다. V3 적용 전에 시작이 실패하면 이전 애플리케이션 이미지로 되돌린 뒤 마이그레이션 오류를 분석합니다. V3가 이미 적용된 경우에는 추가된 테이블을 유지해도 이전 애플리케이션 실행에 영향을 주지 않습니다. 운영 데이터를 자동으로 삭제하지 않습니다.
